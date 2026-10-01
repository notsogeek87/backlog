package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.googlebooks.GoogleBooksService
import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.authorList
import com.davidgcd.backlog.data.local.readStatus
import com.davidgcd.backlog.data.openlibrary.BookSourceError
import com.davidgcd.backlog.data.openlibrary.BookSourceException
import com.davidgcd.backlog.data.openlibrary.OpenLibraryService
import com.davidgcd.backlog.model.Book
import com.davidgcd.backlog.model.BookSource
import com.davidgcd.backlog.model.ReadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class BookRepositoryTest {
    private class Rig(
        openLibrary: (String) -> Any = { Fixtures.DUNE_SEARCH },
        google: (String) -> Any = { Fixtures.GOOGLE_SEARCH },
        initial: List<BookEntity> = emptyList(),
        var clock: Long = 1_000L,
    ) {
        val dao = FakeBookDao(initial)
        val olHttp = FakeHttp(openLibrary)
        val gbHttp = FakeHttp(google)
        val repository = BookRepository(
            dao,
            OpenLibraryService(olHttp.client, "https://ol.test"),
            GoogleBooksService(gbHttp.client, "https://gb.test"),
            now = { clock },
        )
    }

    private fun book(title: String = "Dune", isbn13: String? = null, editionId: String? = null, workId: String? = null, key: String? = null) =
        Book(key = key ?: "isbn:$isbn13", title = title, authors = listOf("Frank Herbert"), isbn13 = isbn13, editionId = editionId, workId = workId)

    // --- search ----------------------------------------------------------------------------

    @Test
    fun `search returns ranked Open Library results and never calls Google Books`() = runTest {
        val rig = Rig()
        val result = rig.repository.search("dune")
        assertEquals(listOf("Dune"), result.books.map { it.title })
        assertFalse(result.fromFallback)
        assertTrue(rig.gbHttp.requests.isEmpty())
    }

    @Test
    fun `search falls back to Google Books when Open Library is down`() = runTest {
        val rig = Rig(openLibrary = { IOException("down") })
        val result = rig.repository.search("fondation")
        assertTrue(result.fromFallback)
        assertEquals(listOf("Fondation"), result.books.map { it.title })
        assertEquals(BookSource.GOOGLE_BOOKS, result.books.single().source)
    }

    @Test
    fun `search falls back to Google Books when Open Library finds nothing`() = runTest {
        val rig = Rig(openLibrary = { Fixtures.EMPTY_OPEN_LIBRARY })
        assertTrue(rig.repository.search("fondation").fromFallback)
    }

    @Test
    fun `no result anywhere is an empty page, not an error`() = runTest {
        val rig = Rig(openLibrary = { Fixtures.EMPTY_OPEN_LIBRARY }, google = { Fixtures.EMPTY_GOOGLE })
        assertTrue(rig.repository.search("zzzzqqqq").books.isEmpty())
    }

    @Test
    fun `one catalogue down and the other empty is still just no result`() = runTest {
        val rig = Rig(openLibrary = { 503 }, google = { Fixtures.EMPTY_GOOGLE })
        assertTrue(rig.repository.search("zzzzqqqq").books.isEmpty())
    }

    @Test
    fun `both catalogues down is an error the UI can word`() = runTest {
        val rig = Rig(openLibrary = { IOException("offline") }, google = { IOException("offline") })
        try {
            rig.repository.search("dune")
            fail()
        } catch (e: BookSourceException) {
            assertEquals(BookSourceError.UNAVAILABLE, e.error)
            assertTrue(e.isNetwork)
        }
    }

    @Test
    fun `the same search is not asked twice, but expires`() = runTest {
        val rig = Rig()
        rig.repository.search("Dune")
        rig.repository.search("dune ")
        assertEquals(1, rig.olHttp.requests.size)
        rig.repository.search("dune", page = 2)
        assertEquals(2, rig.olHttp.requests.size)
        rig.clock += 11 * 60 * 1000L
        rig.repository.search("dune")
        assertEquals(3, rig.olHttp.requests.size)
    }

    @Test
    fun `a blank search is empty without any call`() = runTest {
        val rig = Rig()
        assertTrue(rig.repository.search("  ").books.isEmpty())
        assertTrue(rig.olHttp.requests.isEmpty())
    }

    // --- adding, duplicates ----------------------------------------------------------------

    @Test
    fun `adding a book saves it locally as to-read`() = runTest {
        val rig = Rig()
        val result = rig.repository.add(book(isbn13 = "9782070368228", workId = "OL893415W", editionId = "OL7353617M"))
        assertEquals(BookAddResult.Added("isbn:9782070368228"), result)
        val saved = rig.repository.observeAll().first().single()
        assertEquals(ReadStatus.TO_READ, saved.readStatus)
        assertEquals("OL893415W", saved.workId)
        assertEquals("OL7353617M", saved.editionId)
        assertEquals("9782070368228", saved.isbn13)
        assertEquals(listOf("Frank Herbert"), saved.authorList)
        assertFalse(saved.isFavorite)
    }

    @Test
    fun `adding the same ISBN twice is refused and points to the existing book`() = runTest {
        val rig = Rig()
        rig.repository.add(book(isbn13 = "9782070368228"))
        val again = rig.repository.add(book(title = "Dune (autre titre)", isbn13 = "9782070368228", key = "autre-cle"))
        assertEquals(BookAddResult.Duplicate("isbn:9782070368228"), again)
        assertEquals(1, rig.dao.allBooks().size)
    }

    @Test
    fun `two editions of one work are two entries`() = runTest {
        val rig = Rig()
        assertTrue(rig.repository.add(book(isbn13 = "9782070368228", workId = "OL893415W")) is BookAddResult.Added)
        assertTrue(rig.repository.add(book(isbn13 = "9780441172719", workId = "OL893415W")) is BookAddResult.Added)
        assertEquals(2, rig.dao.allBooks().size)
    }

    @Test
    fun `a work-level hit already saved by title and author is flagged`() = runTest {
        val rig = Rig()
        rig.repository.add(book(editionId = "OL1M", workId = "OL2W", key = "ol:OL1M"))
        val dup = rig.repository.findDuplicate(book(editionId = "OL9M", workId = "OL2W", key = "ol:OL9M"))
        assertNotNull(dup)
        assertEquals("ol:OL1M", dup!!.bookKey)
    }

    // --- status, favorite, rating, delete --------------------------------------------------

    @Test
    fun `status goes from to-read to reading to read and is stored`() = runTest {
        val rig = Rig()
        rig.repository.add(book(isbn13 = "9782070368228"))
        for (status in listOf(ReadStatus.READING, ReadStatus.READ, ReadStatus.ABANDONED)) {
            rig.clock += 10
            rig.repository.setStatus(rig.repository.find("isbn:9782070368228")!!, status)
            val saved = rig.repository.find("isbn:9782070368228")!!
            assertEquals(status, saved.readStatus)
            assertEquals(rig.clock, saved.updatedAt)
        }
    }

    @Test
    fun `favorite is independent of the status`() = runTest {
        val rig = Rig()
        rig.repository.add(book(isbn13 = "9782070368228"))
        rig.repository.setFavorite(rig.repository.find("isbn:9782070368228")!!, true)
        rig.repository.setStatus(rig.repository.find("isbn:9782070368228")!!, ReadStatus.READ)
        val saved = rig.repository.find("isbn:9782070368228")!!
        assertTrue(saved.isFavorite)
        assertEquals(ReadStatus.READ, saved.readStatus)
        rig.repository.setFavorite(saved, false)
        assertFalse(rig.repository.find("isbn:9782070368228")!!.isFavorite)
    }

    @Test
    fun `rating is kept between 1 and 5 and can be cleared`() = runTest {
        val rig = Rig()
        rig.repository.add(book(isbn13 = "9782070368228"))
        rig.repository.setUserRating(rig.repository.find("isbn:9782070368228")!!, 9)
        assertEquals(5, rig.repository.find("isbn:9782070368228")!!.userRating)
        rig.repository.setUserRating(rig.repository.find("isbn:9782070368228")!!, null)
        assertNull(rig.repository.find("isbn:9782070368228")!!.userRating)
    }

    @Test
    fun `removing a book deletes only that book`() = runTest {
        val rig = Rig()
        rig.repository.add(book(isbn13 = "9782070368228"))
        rig.repository.add(book(title = "Fondation", isbn13 = "9782070360536"))
        rig.repository.remove(rig.repository.find("isbn:9782070368228")!!)
        assertEquals(listOf("Fondation"), rig.dao.allBooks().map { it.title })
    }

    // --- details, editions, offline --------------------------------------------------------

    @Test
    fun `opening a search hit adds the edition facts and the description`() = runTest {
        val rig = Rig(openLibrary = { url ->
            when {
                "search.json" in url -> Fixtures.DUNE_SEARCH
                "/books/" in url -> Fixtures.DUNE_EDITION
                "/works/" in url -> Fixtures.DUNE_WORK
                else -> 404
            }
        })
        val hit = rig.repository.search("dune").books.single()
        val detail = rig.repository.fetchRemote(hit.key)!!
        assertEquals("Pocket", detail.publisher)
        assertEquals("9782070368228", detail.isbn13)
        assertEquals("Sur Arrakis.", detail.description)
        assertEquals(hit.key, detail.key)
    }

    @Test
    fun `opening a search hit while the catalogue is down still shows the hit`() = runTest {
        var down = false
        val rig = Rig(openLibrary = { url -> if (down) IOException("offline") else Fixtures.DUNE_SEARCH.also { assertTrue("search.json" in url) } })
        val hit = rig.repository.search("dune").books.single()
        down = true
        assertEquals("Dune", rig.repository.fetchRemote(hit.key)?.title)
        assertNull(rig.repository.fetchRemote("never-seen"))
    }

    @Test
    fun `a saved book is read from the local row with the catalogue down`() = runTest {
        val rig = Rig(openLibrary = { IOException("offline") }, google = { IOException("offline") })
        rig.repository.add(book(isbn13 = "9782070368228"))
        assertEquals("Dune", rig.repository.observe("isbn:9782070368228").first()?.title)
        // Enriching offline is quiet and changes nothing.
        val before = rig.repository.find("isbn:9782070368228")
        rig.repository.enrich("isbn:9782070368228")
        assertEquals(before, rig.repository.find("isbn:9782070368228"))
    }

    @Test
    fun `enriching a saved book fills it in and keeps what the user set`() = runTest {
        val rig = Rig(openLibrary = { url ->
            when {
                "/books/" in url -> Fixtures.DUNE_EDITION
                "/works/" in url -> Fixtures.DUNE_WORK
                else -> 404
            }
        })
        rig.repository.add(book(editionId = "OL7353617M", workId = "OL893415W", key = "ol:OL7353617M"))
        rig.repository.setFavorite(rig.repository.find("ol:OL7353617M")!!, true)
        rig.repository.setStatus(rig.repository.find("ol:OL7353617M")!!, ReadStatus.READING)
        rig.repository.enrich("ol:OL7353617M")
        val saved = rig.repository.find("ol:OL7353617M")!!
        assertEquals("9782070368228", saved.isbn13)
        assertEquals("Pocket", saved.publisher)
        assertEquals(736, saved.pageCount)
        assertEquals("Sur Arrakis.", saved.description)
        assertTrue(saved.isFavorite)
        assertEquals(ReadStatus.READING, saved.readStatus)
        // Same key: the row did not move.
        assertEquals(1, rig.dao.allBooks().size)
    }

    @Test
    fun `editions of a work borrow its authors and are listed once`() = runTest {
        val rig = Rig(openLibrary = { Fixtures.DUNE_EDITIONS })
        val editions = rig.repository.editions(book(workId = "OL893415W", editionId = "OL7353617M", key = "ol:OL7353617M"))
        assertEquals(listOf("isbn:9782070368228", "isbn:9780441172719"), editions.map { it.key })
        assertEquals(listOf("Frank Herbert"), editions.first().authors)
        assertEquals("Ace", editions.last().publisher)
        // Cached: a second look doesn't call again.
        rig.repository.editions(book(workId = "OL893415W"))
        assertEquals(1, rig.olHttp.requests.size)
    }

    @Test
    fun `editions are empty, not an error, when the catalogue is down or the book has no work`() = runTest {
        assertTrue(Rig(openLibrary = { IOException("x") }).repository.editions(book(workId = "OL1W")).isEmpty())
        assertTrue(Rig().repository.editions(book()).isEmpty())
    }

    // --- CSV import completion ---------------------------------------------------------------

    private fun csvRow(title: String = "Dune", isbn13: String? = null) =
        BookEntity(bookKey = "csv-key", title = title, authors = "Frank Herbert", isbn13 = isbn13, status = ReadStatus.READ.name, isFavorite = true, userRating = 5, addedAt = 42L)

    @Test
    fun `a title-only CSV row gets its cover and facts from the catalogue and keeps what the file said`() = runTest {
        val rig = Rig(openLibrary = { url ->
            when {
                "search.json" in url -> Fixtures.DUNE_SEARCH
                "/books/" in url -> Fixtures.DUNE_EDITION
                "/works/" in url -> Fixtures.DUNE_WORK
                else -> 404
            }
        })
        val done = rig.repository.completeFromCatalog(csvRow())
        assertEquals("https://covers.openlibrary.org/b/id/8231856-M.jpg", done.coverUrl)
        assertEquals("Sur Arrakis.", done.description)
        assertEquals("Pocket", done.publisher)
        assertEquals("9782070368228", done.isbn13)
        assertEquals("OL893415W", done.workId)
        assertEquals("csv-key", done.bookKey)
        assertEquals(ReadStatus.READ, done.readStatus)
        assertTrue(done.isFavorite)
        assertEquals(5, done.userRating)
        assertEquals(42L, done.addedAt)
    }

    @Test
    fun `an ISBN-only CSV row is looked up by ISBN`() = runTest {
        val rig = Rig()
        rig.repository.completeFromCatalog(csvRow(isbn13 = "9782070368228"))
        assertTrue(rig.olHttp.requests.first(), "isbn" in rig.olHttp.requests.first())
    }

    @Test
    fun `a complete row is not looked up, and offline or a different title leaves the row untouched`() = runTest {
        val full = csvRow().copy(coverUrl = "c", description = "d")
        val rig = Rig()
        assertEquals(full, rig.repository.completeFromCatalog(full))
        assertTrue(rig.olHttp.requests.isEmpty())
        val offline = Rig(openLibrary = { IOException("x") }, google = { IOException("x") })
        assertEquals(csvRow(), offline.repository.completeFromCatalog(csvRow()))
        assertEquals(csvRow("Tout autre livre"), rig.repository.completeFromCatalog(csvRow("Tout autre livre")))
    }
}
