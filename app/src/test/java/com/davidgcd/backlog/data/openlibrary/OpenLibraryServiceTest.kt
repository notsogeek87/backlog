package com.davidgcd.backlog.data.openlibrary

import com.davidgcd.backlog.data.repository.FakeHttp
import com.davidgcd.backlog.data.repository.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class OpenLibraryServiceTest {
    private fun service(http: FakeHttp) = OpenLibraryService(http.client, baseUrl = "https://openlibrary.test")

    @Test
    fun `a title search asks for a page of French-favoured works with the fields it needs`() = runTest {
        val http = FakeHttp { Fixtures.DUNE_SEARCH }
        val page = service(http).searchBooks("Harry Potter", page = 2, limit = 10)
        assertEquals(1, page.books.size)
        val url = http.requests.single()
        assertTrue(url, url.startsWith("https://openlibrary.test/search.json?"))
        assertTrue(url, "q=Harry%20Potter" in url || "q=Harry+Potter" in url)
        assertTrue(url, "page=2" in url && "limit=10" in url && "lang=fr" in url)
        assertTrue(url, "fields=" in url && "cover_i" in url)
    }

    @Test
    fun `an ISBN search uses the isbn field and keeps the ISBN on the result`() = runTest {
        val http = FakeHttp { Fixtures.DUNE_SEARCH }
        val book = service(http).searchBooks("978-2-07-036822-8").books.single()
        assertTrue(http.requests.single(), "q=isbn%3A9782070368228" in http.requests.single() || "q=isbn:9782070368228" in http.requests.single())
        assertEquals("9782070368228", book.isbn13)
    }

    @Test
    fun `an author search is a plain query`() = runTest {
        val http = FakeHttp { Fixtures.DUNE_SEARCH }
        service(http).searchBooks("Jules Verne")
        assertTrue(http.requests.single(), "q=Jules%20Verne" in http.requests.single() || "q=Jules+Verne" in http.requests.single())
    }

    @Test
    fun `a blank query never calls the API`() = runTest {
        val http = FakeHttp { error("must not be called") }
        assertTrue(service(http).searchBooks("   ").books.isEmpty())
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `offline and server errors surface as an unavailable catalogue`() = runTest {
        try {
            service(FakeHttp { IOException("timeout") }).searchBooks("dune")
            fail()
        } catch (e: BookSourceException) {
            assertEquals(BookSourceError.UNAVAILABLE, e.error)
            assertTrue(e.isNetwork)
        }
        try {
            service(FakeHttp { 503 }).searchBooks("dune")
            fail()
        } catch (e: BookSourceException) {
            assertEquals(BookSourceError.UNAVAILABLE, e.error)
            assertTrue(!e.isNetwork)
        }
    }

    @Test
    fun `getBook merges the edition and its work`() = runTest {
        val http = FakeHttp { url ->
            when {
                "/books/OL7353617M.json" in url -> Fixtures.DUNE_EDITION
                "/works/OL893415W.json" in url -> Fixtures.DUNE_WORK
                else -> 404
            }
        }
        val book = service(http).getBook(workId = null, editionId = "OL7353617M", isbn = null)!!
        assertEquals("9782070368228", book.isbn13)
        assertEquals("Pocket", book.publisher)
        assertEquals("Sur Arrakis.", book.description)
        assertEquals("OL893415W", book.workId)
    }

    @Test
    fun `getBook finds an edition by ISBN`() = runTest {
        val http = FakeHttp { url -> if ("/isbn/9782070368228.json" in url) Fixtures.DUNE_EDITION else 404 }
        assertNotNull(service(http).getBook(null, null, "9782070368228"))
    }

    @Test
    fun `getBook returns what it could read when the work is unknown, and null when nothing is`() = runTest {
        val onlyEdition = FakeHttp { url -> if ("/books/" in url) Fixtures.DUNE_EDITION else 404 }
        assertEquals("Pocket", service(onlyEdition).getBook(null, "OL7353617M", null)?.publisher)
        assertNull(service(FakeHttp { 404 }).getBook("OL1W", "OL1M", null))
        assertNull(service(FakeHttp { error("not called") }).getBook(null, null, null))
    }

    @Test
    fun `editions of a work are listed`() = runTest {
        val http = FakeHttp { Fixtures.DUNE_EDITIONS }
        val editions = service(http).getEditions("OL893415W")
        assertEquals(listOf("OL7353617M", "OL9M"), editions.map { it.editionId })
        assertTrue(http.requests.single().contains("/works/OL893415W/editions.json"))
    }

    @Test
    fun `a Discover list asks the trending or the subject endpoint with a limit`() = runTest {
        val http = FakeHttp { """{"works":[{"key":"/works/OL1W","title":"Dune"}]}""" }
        val svc = service(http)
        assertEquals(1, svc.chart(com.davidgcd.backlog.model.BookChart.TRENDING).size)
        svc.chart(com.davidgcd.backlog.model.BookChart.SCIENCE_FICTION)
        assertTrue(http.requests[0], "/trending/weekly.json" in http.requests[0] && "limit=40" in http.requests[0])
        assertTrue(http.requests[1], "/subjects/science_fiction.json" in http.requests[1])
    }

    @Test
    fun `a Discover list with the catalogue down is an error the screen can word`() = runTest {
        try {
            service(FakeHttp { IOException("offline") }).chart(com.davidgcd.backlog.model.BookChart.FANTASY)
            fail()
        } catch (e: BookSourceException) {
            assertTrue(e.isNetwork)
        }
    }
}
