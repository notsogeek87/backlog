package com.davidgcd.backlog.model

import com.davidgcd.backlog.data.local.BookEntity

/** Le classement personnel des livres (règles de [PersonalRanking] ; le premier est le préféré). */
object BookTop {
    fun order(books: List<BookEntity>): List<BookEntity> = PersonalRanking.order(books, { it.userRank }, { it.addedAt })

    fun move(ordered: List<BookEntity>, index: Int, delta: Int): List<BookEntity> = PersonalRanking.move(ordered, index, delta)

    fun moveTo(ordered: List<BookEntity>, index: Int, target: Int): List<BookEntity> = PersonalRanking.moveTo(ordered, index, target)

    fun changes(newOrder: List<BookEntity>): Map<String, Int> = PersonalRanking.changes(newOrder, { it.userRank }, { it.bookKey })
}
