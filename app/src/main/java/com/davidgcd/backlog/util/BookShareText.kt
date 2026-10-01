package com.davidgcd.backlog.util

import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.authorList
import com.davidgcd.backlog.data.local.readStatus
import com.davidgcd.backlog.data.repository.toBook
import com.davidgcd.backlog.model.ReadStatus

/**
 * Plain-text version of the books list for the share sheet (when the share server can't be reached) — the
 * book twin of [MovieShareText]: rated books first, best note first, then the rest grouped by status.
 */
object BookShareText {
    class Labels(
        val header: () -> String,
        val status: (ReadStatus) -> String,
        val ratings: String,
    )

    fun build(books: List<BookEntity>, labels: Labels): String {
        val rated = books.filter { it.userRating != null }.sortedWith(compareByDescending<BookEntity> { it.userRating }.thenBy { it.title.lowercase() })
        val lines = mutableListOf(labels.header())
        if (rated.isNotEmpty()) {
            lines += ""
            lines += labels.ratings
            rated.forEach { lines += "- ${line(it)}" }
        }
        ReadStatus.entries.forEach { status ->
            val group = books.filter { it.readStatus == status && it.userRating == null }.sortedBy { it.title.lowercase() }
            if (group.isNotEmpty()) {
                lines += ""
                lines += labels.status(status)
                group.forEach { lines += "- ${line(it)}" }
            }
        }
        return lines.joinToString("\n")
    }

    /** One book on one line: "Dune — Frank Herbert (1965) — ★ 4/5 — link". */
    fun line(b: BookEntity): String {
        val author = b.authorList.firstOrNull()?.let { " — $it" }.orEmpty()
        val year = b.publishedYear?.let { " ($it)" }.orEmpty()
        val note = b.userRating?.let { " — ★ $it/5" }.orEmpty()
        val link = b.toBook().catalogUrl?.let { " — $it" }.orEmpty()
        return "${b.title}$author$year$note$link"
    }
}
