package com.davidgcd.backlog.data.csv

import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.authorList
import com.davidgcd.backlog.data.local.joinForStorage
import com.davidgcd.backlog.model.BookKey
import com.davidgcd.backlog.model.BookSource
import com.davidgcd.backlog.model.ReadStatus
import com.davidgcd.backlog.data.repository.toBook

/**
 * The books CSV, both ways: machine-readable only (English headers, enum names for statuses, `;`
 * between list items) so a file written on one device reads back on another — same rule as the games
 * CSV ([CsvColumn]), which is untouched. A row's `type` column is `BOOK`; a row of another type
 * (a games export fed to the books import) is skipped, never misread.
 */
object BookCsv {
    const val TYPE = "BOOK"

    private val COLUMNS = listOf(
        "type", "title", "subtitle", "authors", "isbn13", "isbn10", "publisher", "publishedYear", "pageCount",
        "languages", "subjects", "description", "coverUrl", "workId", "editionId", "source", "status",
        "favorite", "rating", "addedAt",
    )

    fun write(books: List<BookEntity>): List<String> =
        listOf(CsvFormat.writeRow(COLUMNS)) + books.map { CsvFormat.writeRow(row(it)) }

    private fun row(b: BookEntity): List<String> = listOf(
        TYPE,
        b.title,
        b.subtitle.orEmpty(),
        b.authors.orEmpty(),
        b.isbn13.orEmpty(),
        b.isbn10.orEmpty(),
        b.publisher.orEmpty(),
        b.publishedYear?.toString().orEmpty(),
        b.pageCount?.toString().orEmpty(),
        b.languages.orEmpty(),
        b.subjects.orEmpty(),
        // The reader is line-based: a description stays on one line.
        b.description.orEmpty().replace(Regex("\\s*[\\r\\n]+\\s*"), " "),
        b.coverUrl.orEmpty(),
        b.workId.orEmpty(),
        b.editionId.orEmpty(),
        b.source,
        b.status,
        b.isFavorite.toString(),
        b.userRating?.toString().orEmpty(),
        b.addedAt.toString(),
    )

    /** Rows of [lines] (header first) as books to add; rows without a title or of another type are left out. */
    fun parse(lines: List<String>, now: Long = System.currentTimeMillis()): List<BookEntity> {
        val header = CsvFormat.parseRow(lines.firstOrNull() ?: return emptyList()).map { it.trim() }
        if (!header.contains("title")) return emptyList()
        return lines.drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
            val fields = CsvFormat.parseRow(line)
            fun field(name: String): String? = header.indexOf(name).takeIf { it >= 0 }?.let { fields.getOrNull(it) }?.trim()?.ifEmpty { null }

            val type = field("type")
            if (type != null && !type.equals(TYPE, ignoreCase = true)) return@mapNotNull null
            val title = field("title") ?: return@mapNotNull null
            val base = BookEntity(
                bookKey = "",
                title = title,
                subtitle = field("subtitle"),
                authors = field("authors")?.split(';')?.toList()?.joinForStorage(),
                description = field("description"),
                coverUrl = field("coverUrl"),
                publishedYear = field("publishedYear")?.toIntOrNull(),
                publisher = field("publisher"),
                isbn10 = field("isbn10"),
                isbn13 = field("isbn13"),
                pageCount = field("pageCount")?.toIntOrNull()?.takeIf { it > 0 },
                languages = field("languages")?.split(';')?.toList()?.joinForStorage(),
                subjects = field("subjects")?.split(';')?.toList()?.joinForStorage(),
                workId = field("workId"),
                editionId = field("editionId"),
                source = BookSource.fromName(field("source")).name,
                status = ReadStatus.fromName(field("status")).name,
                isFavorite = field("favorite")?.toBooleanStrictOrNull() ?: false,
                userRating = field("rating")?.toIntOrNull()?.takeIf { it in 1..5 },
                addedAt = field("addedAt")?.toLongOrNull() ?: now,
                updatedAt = now,
            )
            // Same key rule as a book found by search, so a re-imported export lands on the same row.
            base.copy(bookKey = BookKey.of(base.toBook()))
        }
    }
}
