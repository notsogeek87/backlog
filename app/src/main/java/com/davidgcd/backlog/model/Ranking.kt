package com.davidgcd.backlog.model

import com.davidgcd.backlog.data.local.GameEntity

/**
 * The personal ranking, first = most loved. Ranked games come first ordered by rank, then the
 * not-yet-ranked ones in the order they were added — so a game added later simply joins the end.
 * Archived games never take part.
 */
object Ranking {
    fun order(games: List<GameEntity>): List<GameEntity> {
        val active = games.filter { !it.isArchived }
        val ranked = active.filter { it.userRank != null }.sortedWith(compareBy({ it.userRank }, { it.addedAt }))
        val unranked = active.filter { it.userRank == null }.sortedBy { it.addedAt }
        return ranked + unranked
    }

    /** Moves the game at [index] by [delta] places (-1 = up), or returns the list unchanged at either end. */
    fun move(ordered: List<GameEntity>, index: Int, delta: Int): List<GameEntity> {
        val target = index + delta
        if (index !in ordered.indices || target !in ordered.indices) return ordered
        return ordered.toMutableList().also { list -> list.add(target, list.removeAt(index)) }
    }

    /** Ranks 1..n for the new order, only for the games whose rank actually changes. */
    fun changes(newOrder: List<GameEntity>): Map<Long, Int> =
        newOrder.mapIndexedNotNull { i, game -> (i + 1).takeIf { it != game.userRank }?.let { game.igdbId to it } }.toMap()
}
