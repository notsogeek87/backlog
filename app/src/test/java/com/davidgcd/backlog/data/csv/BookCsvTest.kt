package com.davidgcd.backlog.data.csv

import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.model.ReadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookCsvTest {
    private val dune = BookEntity(
        bookKey = "isbn:9782070368228",
        title = "Dune, tome 1",
        subtitle = "Le « cycle »",
        authors = "Frank Herbert",
        description = "Sur Arrakis.\nLe désert \"profond\".",
        coverUrl = "https://covers.openlibrary.org/b/id/1-M.jpg",
        publishedYear = 1965,
        publisher = "Pocket",
        isbn10 = "207036822X",
        isbn13 = "9782070368228",
        pageCount = 736,
        languages = "fre",
        subjects = "Science fiction;Désert",
        workId = "OL893415W",
        editionId = "OL7353617M",
        status = ReadStatus.READING.name,
        isFavorite = true,
        userRating = 4,
        addedAt = 1_700_000_000_000,
    )

    @Test
    fun `a book survives an export then an import`() {
        val lines = BookCsv.write(listOf(dune))
        val back = BookCsv.parse(lines).single()
        assertEquals("isbn:9782070368228", back.bookKey)
        assertEquals("Dune, tome 1", back.title)
        assertEquals("Le « cycle »", back.subtitle)
        assertEquals("Frank Herbert", back.authors)
        assertEquals("9782070368228", back.isbn13)
        assertEquals("207036822X", back.isbn10)
        assertEquals("Pocket", back.publisher)
        assertEquals(1965, back.publishedYear)
        assertEquals(736, back.pageCount)
        assertEquals("fre", back.languages)
        assertEquals("Science fiction;Désert", back.subjects)
        assertEquals("OL893415W", back.workId)
        assertEquals("OL7353617M", back.editionId)
        assertEquals(ReadStatus.READING.name, back.status)
        assertTrue(back.isFavorite)
        assertEquals(4, back.userRating)
        assertEquals(1_700_000_000_000, back.addedAt)
    }

    @Test
    fun `the export is one line per book, with the type and the status as machine values`() {
        val lines = BookCsv.write(listOf(dune))
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("type,title,"))
        assertTrue(lines[1].startsWith("BOOK,"))
        assertTrue(lines[1].contains("READING"))
        // A description with line breaks stays on one line.
        assertFalse(lines[1].contains('\n'))
    }

    @Test
    fun `a minimal row with just a type and a title is a valid book`() {
        val rows = BookCsv.parse(listOf("type,title,authors,isbn13,status", "BOOK,Dune,Frank Herbert,9782070368228,TO_READ"))
        val book = rows.single()
        assertEquals("Dune", book.title)
        assertEquals("Frank Herbert", book.authors)
        assertEquals("isbn:9782070368228", book.bookKey)
        assertEquals(ReadStatus.TO_READ.name, book.status)
        assertFalse(book.isFavorite)
        assertNull(book.userRating)
    }

    @Test
    fun `an unknown status falls back to to-read and a bad rating is dropped`() {
        val book = BookCsv.parse(listOf("title,status,rating", "Dune,en cours de route,9")).single()
        assertEquals(ReadStatus.TO_READ.name, book.status)
        assertNull(book.userRating)
    }

    @Test
    fun `rows of another type, without a title, or blank are skipped`() {
        val rows = BookCsv.parse(
            listOf(
                "type,title",
                "GAME,Zelda",
                "BOOK,",
                "",
                "BOOK,Fondation",
            ),
        )
        assertEquals(listOf("Fondation"), rows.map { it.title })
    }

    @Test
    fun `a games export is not read as books and nothing breaks`() {
        // The existing games CSV (name, igdbId, …) has no title column: the books import ignores it entirely.
        assertTrue(BookCsv.parse(listOf("name,igdbId,releaseDate", "Zelda,1022,2017-03-03")).isEmpty())
        assertTrue(BookCsv.parse(emptyList()).isEmpty())
    }

    @Test
    fun `the games CSV columns are unchanged`() {
        assertEquals(
            listOf("name", "igdbId", "releaseDate", "genres", "platforms", "archived", "steamAppId", "status", "rank"),
            CsvColumn.EXPORT_ORDER.map { it.header },
        )
    }
}
