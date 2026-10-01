package com.davidgcd.backlog.model

/**
 * "Is this book already in the backlog?" — checked in this order: ISBN-13, ISBN-10, Open Library edition
 * id, then title + author.
 *
 * Two books that both have an ISBN and don't share one are two *editions* of the same work (Dune in
 * French, Dune in paperback…) and are never duplicates, even with the same title and author; the
 * title + author fallback only applies when one side has no ISBN to tell the editions apart.
 */
object BookDuplicates {
    fun find(candidate: Book, existing: Collection<Book>): Book? {
        val isbn13 = candidate.canonicalIsbn13
        if (isbn13 != null) existing.firstOrNull { it.canonicalIsbn13 == isbn13 }?.let { return it }

        val isbn10 = candidate.canonicalIsbn10
        if (isbn10 != null) existing.firstOrNull { it.canonicalIsbn10 == isbn10 }?.let { return it }

        val edition = candidate.editionId
        if (edition != null) existing.firstOrNull { it.editionId == edition }?.let { return it }

        val title = BookText.normalize(candidate.title)
        val author = candidate.authors.firstOrNull()?.let(BookText::normalize)
        if (title.isEmpty()) return null
        return existing.firstOrNull { other ->
            // Both ISBN-ed (and not equal, or we'd have returned): distinct editions.
            if (candidate.hasIsbn && other.hasIsbn) return@firstOrNull false
            BookText.normalize(other.title) == title && sameAuthor(author, other.authors.firstOrNull()?.let(BookText::normalize))
        }
    }

    /** An unknown author on either side doesn't contradict the match. */
    private fun sameAuthor(a: String?, b: String?): Boolean = a.isNullOrEmpty() || b.isNullOrEmpty() || a == b
}
