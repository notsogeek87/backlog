package com.davidgcd.backlog.model

import com.davidgcd.backlog.util.BookImage
import com.davidgcd.backlog.util.BookLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookTest {
    private fun book(
        title: String = "Dune",
        authors: List<String> = listOf("Frank Herbert"),
        isbn13: String? = null,
        isbn10: String? = null,
        workId: String? = null,
        editionId: String? = null,
        key: String = "k",
    ) = Book(key = key, title = title, authors = authors, isbn13 = isbn13, isbn10 = isbn10, workId = workId, editionId = editionId)

    // --- keys ------------------------------------------------------------------------------

    @Test
    fun `key prefers the ISBN-13, then the edition, then the work`() {
        assertEquals("isbn:9782070368228", BookKey.of(book(isbn13 = "9782070368228", editionId = "OL1M")))
        assertEquals("ol:OL1M", BookKey.of(book(editionId = "OL1M", workId = "OL2W")))
        assertEquals("work:OL2W", BookKey.of(book(workId = "OL2W")))
        assertTrue(BookKey.of(book()).startsWith("ta:"))
    }

    @Test
    fun `an ISBN-10 and its ISBN-13 give one key`() {
        assertEquals(BookKey.of(book(isbn10 = "207036822X")), BookKey.of(book(isbn13 = "9782070368228")))
    }

    @Test
    fun `title and author key ignores case and accents`() {
        assertEquals(BookKey.of(book(title = "Les Misérables", authors = listOf("Victor Hugo"))), BookKey.of(book(title = "les miserables", authors = listOf("VICTOR HUGO"))))
        assertNotEquals(BookKey.of(book(title = "Dune")), BookKey.of(book(title = "Dune Messiah")))
    }

    // --- duplicates ------------------------------------------------------------------------

    @Test
    fun `same ISBN-13 is a duplicate`() {
        val saved = book(isbn13 = "9782070368228", key = "a")
        assertEquals(saved, BookDuplicates.find(book(isbn13 = "978-2-07-036822-8", title = "Autre titre"), listOf(saved)))
    }

    @Test
    fun `an ISBN-10 matches the ISBN-13 of the same book`() {
        val saved = book(isbn13 = "9782070368228", key = "a")
        assertEquals(saved, BookDuplicates.find(book(isbn10 = "207036822X"), listOf(saved)))
        val savedTen = book(isbn10 = "207036822X", key = "b")
        assertEquals(savedTen, BookDuplicates.find(book(isbn13 = "9782070368228"), listOf(savedTen)))
    }

    @Test
    fun `same Open Library edition is a duplicate`() {
        val saved = book(editionId = "OL1M", key = "a")
        assertEquals(saved, BookDuplicates.find(book(editionId = "OL1M", title = "Dune (autre titre)"), listOf(saved)))
    }

    @Test
    fun `two editions with different ISBNs are not duplicates even with the same title and author`() {
        val french = book(isbn13 = "9782070368228", workId = "OL893415W", key = "a")
        val paperback = book(isbn13 = "9780441172719", workId = "OL893415W")
        assertNull(BookDuplicates.find(paperback, listOf(french)))
    }

    @Test
    fun `without an ISBN on one side, title and author decide`() {
        val saved = book(isbn13 = "9782070368228", key = "a")
        assertEquals(saved, BookDuplicates.find(book(title = "DUNE", authors = listOf("frank herbert")), listOf(saved)))
        assertNull(BookDuplicates.find(book(title = "Dune Messiah"), listOf(saved)))
        assertNull(BookDuplicates.find(book(authors = listOf("Brian Herbert")), listOf(saved)))
    }

    @Test
    fun `an unknown author does not prevent the title match`() {
        val saved = book(authors = emptyList(), key = "a")
        assertEquals(saved, BookDuplicates.find(book(), listOf(saved)))
    }

    @Test
    fun `a book is not a duplicate of an empty list`() {
        assertNull(BookDuplicates.find(book(), emptyList()))
    }

    // --- ranking ---------------------------------------------------------------------------

    @Test
    fun `exact title first, then covers and French editions, otherwise the catalogue order`() {
        val partial = book(title = "Dune, tome 2", key = "partial").copy(coverUrl = "x")
        val exact = book(title = "Dune", key = "exact")
        val covered = book(title = "Autre livre", key = "covered").copy(coverUrl = "x")
        val french = book(title = "Un roman", key = "french").copy(languages = listOf("fre"))
        val plainA = book(title = "Roman A", key = "a")
        val plainB = book(title = "Roman B", key = "b")
        val ranked = BookRanking.rank(listOf(plainA, plainB, french, covered, partial, exact), "dune").map { it.key }
        assertEquals(listOf("exact", "partial", "covered", "french", "a", "b"), ranked)
    }

    @Test
    fun `ranking ignores accents and case in the query`() {
        val ranked = BookRanking.rank(listOf(book(title = "Autre", key = "x"), book(title = "Les Misérables", key = "m")), "LES MISERABLES")
        assertEquals("m", ranked.first().key)
    }

    // --- enrichment ------------------------------------------------------------------------

    @Test
    fun `a fresher read fills what is missing and keeps what the user saw`() {
        val hit = Book(key = "ol:OL1M", title = "Dune", authors = listOf("Frank Herbert"), workId = "OL2W", editionId = "OL1M", pageCount = 600, coverUrl = "cover")
        val edition = Book(key = "", title = "Dune (titre de l'édition)", authors = listOf("Autre"), publisher = "Pocket", isbn13 = "9782070368228",
            pageCount = 736, languages = listOf("fre"), description = "Sur Arrakis.", coverUrl = "other")
        val merged = hit.enrichedWith(edition)
        assertEquals("Dune", merged.title)
        assertEquals(listOf("Frank Herbert"), merged.authors)
        assertEquals("cover", merged.coverUrl)
        assertEquals("Pocket", merged.publisher)
        assertEquals("9782070368228", merged.isbn13)
        assertEquals(736, merged.pageCount)
        assertEquals(listOf("fre"), merged.languages)
        assertEquals("Sur Arrakis.", merged.description)
        // The key names the row and never moves.
        assertEquals("ol:OL1M", merged.key)
    }

    @Test
    fun `an edition borrows the authors of its work but keeps its own facts`() {
        val work = book(workId = "OL2W").copy(description = "Résumé", subjects = listOf("SF"), publisher = "Autre")
        val edition = Book(key = "", title = "Dune (poche)", publisher = "Pocket", isbn13 = "9782070368228")
        val merged = edition.asEditionOf(work)
        assertEquals(listOf("Frank Herbert"), merged.authors)
        assertEquals("Résumé", merged.description)
        assertEquals("Pocket", merged.publisher)
        assertEquals("Dune (poche)", merged.title)
        assertEquals("OL2W", merged.workId)
    }

    // --- covers & languages ----------------------------------------------------------------

    @Test
    fun `cover size can be changed on an Open Library cover`() {
        assertEquals("https://covers.openlibrary.org/b/id/8231856-L.jpg", BookImage.sized(BookImage.byCoverId(8231856), 'L'))
        assertEquals("https://covers.openlibrary.org/b/olid/OL1M-S.jpg?default=false", BookImage.sized(BookImage.byEditionId("OL1M"), 'S'))
        assertEquals("https://books.google.com/x?id=1", BookImage.sized("https://books.google.com/x?id=1", 'L'))
        assertNull(BookImage.sized(null))
        assertNull(BookImage.sized(" "))
    }

    @Test
    fun `languages are shown by their French name`() {
        assertEquals("Français", BookLanguages.label("fre"))
        assertEquals("Français", BookLanguages.label("fr"))
        assertEquals("Anglais", BookLanguages.label("eng"))
        assertEquals("Allemand", BookLanguages.label("ger"))
        assertEquals("XXX", BookLanguages.label("xxx"))
        assertEquals(listOf("Français"), BookLanguages.labels(listOf("fre", "fra", "")))
        assertTrue(BookLanguages.isFrench("fre"))
        assertFalse(BookLanguages.isFrench("eng"))
    }
}
