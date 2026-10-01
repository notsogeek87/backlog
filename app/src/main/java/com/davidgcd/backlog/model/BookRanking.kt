package com.davidgcd.backlog.model

import com.davidgcd.backlog.util.BookLanguages

/**
 * Orders catalogue hits for a query without second-guessing the catalogue's own relevance: an exact
 * title match first, then a title that starts with the query, then hits that have a cover and a
 * French edition. Ties keep the catalogue's order (the sort is stable), so there is no scoring
 * system to tune.
 */
object BookRanking {
    fun rank(books: List<Book>, query: String): List<Book> {
        val q = BookText.normalize(query)
        return books.sortedByDescending { score(it, q) }
    }

    internal fun score(book: Book, normalizedQuery: String): Int {
        var score = 0
        val title = BookText.normalize(book.title)
        if (normalizedQuery.isNotEmpty()) {
            if (title == normalizedQuery) score += 8 else if (title.startsWith(normalizedQuery)) score += 4
        }
        if (book.coverUrl != null) score += 2
        if (book.languages.any(BookLanguages::isFrench)) score += 1
        return score
    }
}
