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

    private fun library(vararg rows: BookEntity) = listOf(CsvFormat.writeRow(BookCsv.HEADER)) + rows.map { CsvFormat.writeRow(BookCsv.row(it)) }

    @Test
    fun `a book survives an export then an import`() {
        val back = BookCsv.parse(library(dune)).single()
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
    fun `the library header keeps the games columns, adds type in front and the book columns after`() {
        val gameHeaders = CsvColumn.EXPORT_ORDER.map { it.header }
        assertEquals(listOf("type") + gameHeaders + BookCsv.EXTRA_COLUMNS, BookCsv.HEADER)
        assertEquals(
            listOf("name", "igdbId", "releaseDate", "genres", "platforms", "archived", "steamAppId", "status", "rank"),
            gameHeaders,
        )
    }

    @Test
    fun `a book row has one value per column, is typed BOOK and stays on one line`() {
        val row = BookCsv.row(dune)
        assertEquals(BookCsv.HEADER.size, row.size)
        assertEquals("BOOK", row[0])
        assertEquals("Dune, tome 1", row[BookCsv.HEADER.indexOf("name")])
        assertEquals("READING", row[BookCsv.HEADER.indexOf("status")])
        assertEquals("", row[BookCsv.HEADER.indexOf("igdbId")])
        assertFalse(CsvFormat.writeRow(row).contains('\n'))
    }

    @Test
    fun `a file mixing games and books gives only the books to the book reader`() {
        val gameLine = CsvFormat.writeRow(listOf("GAME", "Zelda", "1022") + List(BookCsv.HEADER.size - 3) { "" })
        val rows = BookCsv.parse(library(dune) + gameLine)
        assertEquals(listOf("Dune, tome 1"), rows.map { it.title })
    }

    @Test
    fun `an old games-only file without a type column has no books`() {
        assertTrue(BookCsv.parse(listOf("name,igdbId,releaseDate", "Zelda,1022,2017-03-03")).isEmpty())
        assertFalse(BookCsv.isBook(null))
        assertTrue(BookCsv.parse(emptyList()).isEmpty())
    }

    @Test
    fun `a hand-written row with just a type and a title is a valid book`() {
        val book = BookCsv.parse(listOf("type,title,authors,isbn13,status", "BOOK,Dune,Frank Herbert,9782070368228,TO_READ")).single()
        assertEquals("Dune", book.title)
        assertEquals("Frank Herbert", book.authors)
        assertEquals("isbn:9782070368228", book.bookKey)
        assertEquals(ReadStatus.TO_READ.name, book.status)
        assertFalse(book.isFavorite)
        assertNull(book.userRating)
    }

    @Test
    fun `an unknown status falls back to to-read and a bad rating is dropped`() {
        val book = BookCsv.parse(listOf("type,name,status,rating", "BOOK,Dune,en cours de route,9")).single()
        assertEquals(ReadStatus.TO_READ.name, book.status)
        assertNull(book.userRating)
    }

    @Test
    fun `book rows without a title and blank lines are skipped`() {
        val rows = BookCsv.parse(listOf("type,name", "BOOK,", "", "BOOK,Fondation"))
        assertEquals(listOf("Fondation"), rows.map { it.title })
    }
}
