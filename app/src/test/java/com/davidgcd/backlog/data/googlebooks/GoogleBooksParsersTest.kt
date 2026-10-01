package com.davidgcd.backlog.data.googlebooks

import com.davidgcd.backlog.model.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleBooksParsersTest {
    private val json = """{"totalItems":2,"items":[
      {"id":"abc123","volumeInfo":{"title":"Dune","subtitle":"Tome 1","authors":["Frank Herbert"],"publisher":"Pocket",
        "publishedDate":"1990-03-01","description":"Sur Arrakis.","pageCount":736,"language":"fr","categories":["Fiction"],
        "industryIdentifiers":[{"type":"ISBN_10","identifier":"207036822X"},{"type":"ISBN_13","identifier":"9782070368228"}],
        "imageLinks":{"thumbnail":"http://books.google.com/books/content?id=abc123&zoom=1&edge=curl&source=gbs_api"}}},
      {"id":"noisbn","volumeInfo":{"title":"Livre sans ISBN"}},
      {"id":"notitle","volumeInfo":{}}
    ]}"""

    @Test
    fun `reads the volume info and the ISBNs`() {
        val dune = GoogleBooksParsers.parseSearch(json).books[0]
        assertEquals("Dune", dune.title)
        assertEquals("Tome 1", dune.subtitle)
        assertEquals(listOf("Frank Herbert"), dune.authors)
        assertEquals("Pocket", dune.publisher)
        assertEquals(1990, dune.publishedYear)
        assertEquals(736, dune.pageCount)
        assertEquals("9782070368228", dune.isbn13)
        assertEquals("207036822X", dune.isbn10)
        assertEquals(listOf("fr"), dune.languages)
        assertEquals(listOf("Fiction"), dune.subjects)
        assertEquals(BookSource.GOOGLE_BOOKS, dune.source)
        assertEquals("isbn:9782070368228", dune.key)
    }

    @Test
    fun `thumbnails are https without the page curl`() {
        val cover = GoogleBooksParsers.parseSearch(json).books[0].coverUrl
        assertEquals("https://books.google.com/books/content?id=abc123&zoom=1&source=gbs_api", cover)
    }

    @Test
    fun `a volume without ISBN or cover is keyed by its Google id`() {
        val book = GoogleBooksParsers.parseSearch(json).books[1]
        assertEquals("gb:noisbn", book.key)
        assertNull(book.coverUrl)
        assertNull(book.isbn13)
        assertTrue(book.authors.isEmpty())
    }

    @Test
    fun `volumes without a title are dropped and garbage gives nothing`() {
        assertEquals(2, GoogleBooksParsers.parseSearch(json).books.size)
        assertTrue(GoogleBooksParsers.parseSearch("nope").books.isEmpty())
        assertTrue(GoogleBooksParsers.parseSearch("""{"totalItems":0}""").books.isEmpty())
        assertFalse(GoogleBooksParsers.parseSearch(json).hasMore)
    }
}
