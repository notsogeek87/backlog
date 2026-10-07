package com.davidgcd.backlog.model

import com.davidgcd.backlog.data.local.BookEntity

/** Le classement personnel des livres — mêmes règles que [Ranking] pour les jeux (le premier est le préféré). */
object BookTop {
    fun order(books: List<BookEntity>): List<BookEntity> {
        val ranked = books.filter { it.userRank != null }.sortedWith(compareBy({ it.userRank }, { it.addedAt }))
        val unranked = books.filter { it.userRank == null }.sortedBy { it.addedAt }
        return ranked + unranked
    }

    fun move(ordered: List<BookEntity>, index: Int, delta: Int): List<BookEntity> = moveTo(ordered, index, index + delta)

    fun moveTo(ordered: List<BookEntity>, index: Int, target: Int): List<BookEntity> {
        if (index !in ordered.indices || target !in ordered.indices || index == target) return ordered
        return ordered.toMutableList().also { list -> list.add(target, list.removeAt(index)) }
    }

    /** Ranks 1..n for the new order, only for the books whose rank actually changes. */
    fun changes(newOrder: List<BookEntity>): Map<String, Int> =
        newOrder.mapIndexedNotNull { i, b -> (i + 1).takeIf { it != b.userRank }?.let { b.bookKey to it } }.toMap()
}
