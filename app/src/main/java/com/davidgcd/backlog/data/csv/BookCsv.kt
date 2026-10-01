package com.davidgcd.backlog.data.csv

import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.joinForStorage
import com.davidgcd.backlog.data.repository.toBook
import com.davidgcd.backlog.model.BookKey
import com.davidgcd.backlog.model.BookSource
import com.davidgcd.backlog.model.ReadStatus

/**
 * How a book is a row of the library CSV — the one file for games and books, told apart by the `type`
 * column (`GAME` / `BOOK`). A file without a `type` column (every export written before books existed)
 * is all games. Books reuse the games' `name` (the title), `genres` (the subjects) and `status`
 * columns, and add their own after them ([EXTRA_COLUMNS]); `;` separates list items. Machine-readable
 * only, like [CsvColumn]: English headers, enum names for statuses.
 */
object BookCsv {
    const val TYPE = "BOOK"
    const val GAME_TYPE = "GAME"
    const val TYPE_HEADER = "type"

    /** Book-only columns, appended after the games' ones. */
    val EXTRA_COLUMNS = listOf(
        "subtitle", "authors", "isbn13", "isbn10", "publisher", "publishedYear", "pageCount", "languages",
        "description", "coverUrl", "workId", "editionId", "source", "favorite", "rating", "addedAt",
    )

    /** The header of the whole library file: `type`, the games' columns (unchanged), then the books' extras. */
    val HEADER: List<String> = listOf(TYPE_HEADER) + CsvColumn.EXPORT_ORDER.map { it.header } + EXTRA_COLUMNS

    /** One book as a full library row (the games' columns a book doesn't have are left empty). */
    fun row(b: BookEntity): List<String> {
        val values = mapOf(
            TYPE_HEADER to TYPE,
            CsvColumn.NAME.header to b.title,
            CsvColumn.GENRES.header to b.subjects.orEmpty(),
            CsvColumn.STATUS.header to b.status,
            "subtitle" to b.subtitle.orEmpty(),
            "authors" to b.authors.orEmpty(),
            "isbn13" to b.isbn13.orEmpty(),
            "isbn10" to b.isbn10.orEmpty(),
            "publisher" to b.publisher.orEmpty(),
            "publishedYear" to b.publishedYear?.toString().orEmpty(),
            "pageCount" to b.pageCount?.toString().orEmpty(),
            "languages" to b.languages.orEmpty(),
            // The reader is line-based: a description stays on one line.
            "description" to b.description.orEmpty().replace(Regex("\\s*[\\r\\n]+\\s*"), " "),
            "coverUrl" to b.coverUrl.orEmpty(),
            "workId" to b.workId.orEmpty(),
            "editionId" to b.editionId.orEmpty(),
            "source" to b.source,
            "favorite" to b.isFavorite.toString(),
            "rating" to b.userRating?.toString().orEmpty(),
            "addedAt" to b.addedAt.toString(),
        )
        return HEADER.map { values[it].orEmpty() }
    }

    /** Whether a row is a book: only an explicit `BOOK` type says so (no type = a game, as in old files). */
    fun isBook(type: String?): Boolean = type?.trim().equals(TYPE, ignoreCase = true)

    /**
     * A book from one row, read through [field] (column name → trimmed value or null). `title` is accepted
     * for `name` and `subjects` for `genres`, so a hand-written file works too. Null without a title.
     */
    fun fromRow(now: Long = System.currentTimeMillis(), field: (String) -> String?): BookEntity? {
        val title = field(CsvColumn.NAME.header) ?: field("title") ?: return null
        val base = BookEntity(
            bookKey = "",
            title = title,
            subtitle = field("subtitle"),
            authors = field("authors")?.split(';')?.joinForStorage(),
            description = field("description"),
            coverUrl = field("coverUrl"),
            publishedYear = field("publishedYear")?.toIntOrNull(),
            publisher = field("publisher"),
            isbn10 = field("isbn10"),
            isbn13 = field("isbn13"),
            pageCount = field("pageCount")?.toIntOrNull()?.takeIf { it > 0 },
            languages = field("languages")?.split(';')?.joinForStorage(),
            subjects = (field(CsvColumn.GENRES.header) ?: field("subjects"))?.split(';')?.joinForStorage(),
            workId = field("workId"),
            editionId = field("editionId"),
            source = BookSource.fromName(field("source")).name,
            status = ReadStatus.fromName(field(CsvColumn.STATUS.header)).name,
            isFavorite = field("favorite")?.toBooleanStrictOrNull() ?: false,
            userRating = field("rating")?.toIntOrNull()?.takeIf { it in 1..5 },
            addedAt = field("addedAt")?.toLongOrNull() ?: now,
            updatedAt = now,
        )
        // Same key rule as a book found by search, so a re-imported export lands on the same row.
        return base.copy(bookKey = BookKey.of(base.toBook()))
    }

    /** Every book row of a whole library file ([lines], header first); other types and title-less rows are left out. */
    fun parse(lines: List<String>, now: Long = System.currentTimeMillis()): List<BookEntity> {
        val header = CsvFormat.parseRow(lines.firstOrNull() ?: return emptyList()).map { it.trim() }
        return lines.drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
            val fields = CsvFormat.parseRow(line)
            val field = { name: String -> header.indexOf(name).takeIf { it >= 0 }?.let { fields.getOrNull(it) }?.trim()?.ifEmpty { null } }
            if (!isBook(field(TYPE_HEADER))) null else fromRow(now, field)
        }
    }
}
