package com.davidgcd.backlog.model

import com.davidgcd.backlog.data.local.MovieEntity

/** The personal ranking of films and series — same rules as [Ranking] for games. */
object MovieRanking {
    fun order(movies: List<MovieEntity>): List<MovieEntity> {
        val active = movies.filter { !it.isArchived }
        val ranked = active.filter { it.userRank != null }.sortedWith(compareBy({ it.userRank }, { it.addedAt }))
        val unranked = active.filter { it.userRank == null }.sortedBy { it.addedAt }
        return ranked + unranked
    }

    fun move(ordered: List<MovieEntity>, index: Int, delta: Int): List<MovieEntity> = moveTo(ordered, index, index + delta)

    fun moveTo(ordered: List<MovieEntity>, index: Int, target: Int): List<MovieEntity> {
        if (index !in ordered.indices || target !in ordered.indices || index == target) return ordered
        return ordered.toMutableList().also { list -> list.add(target, list.removeAt(index)) }
    }

    /** Ranks 1..n for the new order, only for the titles whose rank actually changes. */
    fun changes(newOrder: List<MovieEntity>): Map<String, Int> =
        newOrder.mapIndexedNotNull { i, m -> (i + 1).takeIf { it != m.userRank }?.let { m.imdbId to it } }.toMap()
}
