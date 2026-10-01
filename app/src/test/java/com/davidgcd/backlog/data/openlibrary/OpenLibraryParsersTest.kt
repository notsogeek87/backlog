package com.davidgcd.backlog.data.openlibrary

import com.davidgcd.backlog.model.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLibraryParsersTest {
    private val searchJson = """{"numFound":120,"start":0,"docs":[
      {"key":"/works/OL893415W","title":"Dune","author_name":["Frank Herbert"],"first_publish_year":1965,
       "cover_i":8231856,"cover_edition_key":"OL7353617M","language":["eng","fre","spa"],
       "number_of_pages_median":612,"subject":["Science fiction","Deserts","Arrakis"]},
      {"key":"/works/OL1W","title":"Dune, la bande dessinée","subtitle":"Tome 1","author_name":["Frank Herbert","Brian Herbert"],
       "language":["fre"]},
      {"key":"/works/OL2W","title":""},
      {"title":"No key"}
    ]}"""

    @Test
    fun `search reads one book per work with its cover, authors and year`() {
        val page = OpenLibraryParsers.parseSearch(searchJson)
        assertEquals(3, page.books.size)
        assertTrue(page.hasMore)
        val dune = page.books[0]
        assertEquals("Dune", dune.title)
        assertEquals(listOf("Frank Herbert"), dune.authors)
        assertEquals(1965, dune.publishedYear)
        assertEquals("OL893415W", dune.workId)
        assertEquals("OL7353617M", dune.editionId)
        assertEquals("https://covers.openlibrary.org/b/id/8231856-M.jpg", dune.coverUrl)
        assertEquals(612, dune.pageCount)
        assertEquals(listOf("Science-fiction", "Déserts", "Arrakis"), dune.subjects)
        assertEquals(BookSource.OPEN_LIBRARY, dune.source)
        assertEquals("ol:OL7353617M", dune.key)
    }

    @Test
    fun `search does not guess the ISBN of a work or the language of an edition`() {
        val dune = OpenLibraryParsers.parseSearch(searchJson).books[0]
        assertNull(dune.isbn13)
        assertNull(dune.isbn10)
        // Three languages = the work's, not this edition's.
        assertTrue(dune.languages.isEmpty())
        // A single one is kept.
        assertEquals(listOf("fre"), OpenLibraryParsers.parseSearch(searchJson).books[1].languages)
    }

    @Test
    fun `search with no cover id and no edition has no cover and a work key`() {
        val book = OpenLibraryParsers.parseSearch(searchJson).books[1]
        assertNull(book.coverUrl)
        assertEquals("Tome 1", book.subtitle)
        assertEquals(listOf("Frank Herbert", "Brian Herbert"), book.authors)
        assertEquals("work:OL1W", book.key)
    }

    @Test
    fun `search by ISBN keeps the ISBN the user typed`() {
        val book = OpenLibraryParsers.parseSearch(searchJson, queryIsbn = "9782070368228").books[0]
        assertEquals("9782070368228", book.isbn13)
        assertEquals("207036822X", book.isbn10)
        assertEquals("isbn:9782070368228", book.key)
    }

    @Test
    fun `search tells whether there is another page`() {
        assertFalse(OpenLibraryParsers.parseSearch("""{"numFound":20,"docs":[{"key":"/works/OL1W","title":"A"}]}""", page = 1).hasMore)
        assertTrue(OpenLibraryParsers.parseSearch("""{"numFound":21,"docs":[{"key":"/works/OL1W","title":"A"}]}""", page = 1).hasMore)
        assertFalse(OpenLibraryParsers.parseSearch("""{"numFound":21,"docs":[{"key":"/works/OL1W","title":"A"}]}""", page = 2).hasMore)
    }

    @Test
    fun `search survives garbage and empty answers`() {
        assertTrue(OpenLibraryParsers.parseSearch("not json").books.isEmpty())
        assertTrue(OpenLibraryParsers.parseSearch("{}").books.isEmpty())
        assertTrue(OpenLibraryParsers.parseSearch("""{"numFound":0,"docs":[]}""").books.isEmpty())
    }

    @Test
    fun `work description is read as a plain string or as a typed value`() {
        val plain = OpenLibraryParsers.parseWork("""{"key":"/works/OL893415W","title":"Dune","description":"Sur Arrakis."}""")
        assertEquals("Sur Arrakis.", plain?.description)
        assertEquals("OL893415W", plain?.workId)
        val typed = OpenLibraryParsers.parseWork(
            """{"key":"/works/OL893415W","title":"Dune","description":{"type":"/type/text","value":" Sur Arrakis. "},
               "subjects":["Science fiction"],"first_publish_date":"1965","covers":[-1,8231856]}""",
        )
        assertEquals("Sur Arrakis.", typed?.description)
        assertEquals(listOf("Science-fiction"), typed?.subjects)
        assertEquals(1965, typed?.publishedYear)
        assertEquals("https://covers.openlibrary.org/b/id/8231856-M.jpg", typed?.coverUrl)
        assertNull(OpenLibraryParsers.parseWork("""{"key":"/works/OL1W","title":"X"}""")?.description)
        assertNull(OpenLibraryParsers.parseWork("nope"))
    }

    @Test
    fun `edition carries the ISBNs, publisher, pages and language`() {
        val edition = OpenLibraryParsers.parseEdition(
            """{"key":"/books/OL7353617M","title":"Dune","subtitle":"Tome 1","publishers":["Pocket"],"publish_date":"March 1990",
               "number_of_pages":736,"isbn_10":["207036822X"],"isbn_13":["978-2-07-036822-8"],
               "languages":[{"key":"/languages/fre"}],"works":[{"key":"/works/OL893415W"}],"covers":[123]}""",
        )!!
        assertEquals("OL7353617M", edition.editionId)
        assertEquals("OL893415W", edition.workId)
        assertEquals("Pocket", edition.publisher)
        assertEquals(1990, edition.publishedYear)
        assertEquals(736, edition.pageCount)
        assertEquals("9782070368228", edition.isbn13)
        assertEquals("207036822X", edition.isbn10)
        assertEquals(listOf("fre"), edition.languages)
        assertEquals("Tome 1", edition.subtitle)
    }

    @Test
    fun `edition with missing or invalid data stays partial`() {
        val edition = OpenLibraryParsers.parseEdition("""{"key":"/books/OL1M","title":"X","isbn_13":["123"],"number_of_pages":0}""")!!
        assertNull(edition.isbn13)
        assertNull(edition.pageCount)
        assertNull(edition.publisher)
        assertTrue(edition.languages.isEmpty())
        assertNull(OpenLibraryParsers.parseEdition("""{"key":"/books/OL1M"}"""))
    }

    @Test
    fun `editions list skips records that cannot be told apart`() {
        val editions = OpenLibraryParsers.parseEditions(
            """{"entries":[
              {"key":"/books/OL1M","title":"Dune","isbn_13":["9782070368228"]},
              {"key":"/books/OL2M","title":"Dune"},
              {"title":"Dune"}
            ]}""",
        )
        assertEquals(listOf("OL1M", "OL2M"), editions.map { it.editionId })
    }

    @Test
    fun `year is found in the usual date spellings`() {
        assertEquals(1982, OpenLibraryParsers.year("[1982?]"))
        assertEquals(1990, OpenLibraryParsers.year("1990-03-01"))
        assertEquals(2004, OpenLibraryParsers.year("Jan 5, 2004"))
        assertNull(OpenLibraryParsers.year("n.d."))
        assertNull(OpenLibraryParsers.year(null))
    }
}
