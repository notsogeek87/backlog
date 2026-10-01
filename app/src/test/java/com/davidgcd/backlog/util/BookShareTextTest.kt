package com.davidgcd.backlog.util

import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.model.ReadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookShareTextTest {
    private val labels = BookShareText.Labels(
        header = { "Ma liste de livres (4 livres)" },
        status = { status -> mapOf(ReadStatus.TO_READ to "À lire", ReadStatus.READING to "En cours", ReadStatus.READ to "Lu", ReadStatus.ABANDONED to "Abandonné")[status]!! },
        ratings = "Mes notes",
    )

    private fun book(title: String, status: ReadStatus = ReadStatus.TO_READ, rating: Int? = null, author: String? = "Frank Herbert") =
        BookEntity(bookKey = title, title = title, authors = author, status = status.name, userRating = rating, publishedYear = 1965, workId = "OL1W")

    @Test
    fun `rated books come first, best note first, then the rest grouped by status`() {
        val text = BookShareText.build(
            listOf(book("Zeta", ReadStatus.TO_READ), book("Bof", ReadStatus.READ, rating = 2), book("Top", ReadStatus.READ, rating = 5), book("Alpha", ReadStatus.READING)),
            labels,
        )
        val lines = text.lines()
        assertEquals("Ma liste de livres (4 livres)", lines.first())
        assertTrue(text.indexOf("Mes notes") < text.indexOf("Top"))
        assertTrue(text.indexOf("Top") < text.indexOf("Bof"))
        assertTrue(text.indexOf("Bof") < text.indexOf("À lire"))
        assertTrue(text.indexOf("En cours") < text.indexOf("Alpha") && text.indexOf("Alpha") < text.indexOf("À lire") || text.indexOf("À lire") < text.indexOf("En cours"))
        // A rated book is listed once, in the notes.
        assertEquals(1, Regex("Top").findAll(text).count())
        assertTrue(text.contains("★ 5/5"))
    }

    @Test
    fun `a line has the title, author, year, note and the catalogue link`() {
        assertEquals(
            "Dune — Frank Herbert (1965) — ★ 4/5 — https://openlibrary.org/works/OL1W",
            BookShareText.line(book("Dune", rating = 4)),
        )
        assertEquals("Dune (1965) — https://openlibrary.org/works/OL1W", BookShareText.line(book("Dune", author = null)))
    }

    @Test
    fun `an empty list is just the header`() {
        assertEquals("Ma liste de livres (4 livres)", BookShareText.build(emptyList(), labels))
    }
}
