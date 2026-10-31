package com.davidgcd.backlog.model

import com.davidgcd.backlog.data.local.MovieEntity

/** The personal ranking of films and series ([PersonalRanking] rules). Archived titles never take part. */
object MovieRanking {
    fun order(movies: List<MovieEntity>): List<MovieEntity> =
        PersonalRanking.order(movies.filter { !it.isArchived }, { it.userRank }, { it.addedAt })

    fun move(ordered: List<MovieEntity>, index: Int, delta: Int): List<MovieEntity> = PersonalRanking.move(ordered, index, delta)

    fun moveTo(ordered: List<MovieEntity>, index: Int, target: Int): List<MovieEntity> = PersonalRanking.moveTo(ordered, index, target)

    fun changes(newOrder: List<MovieEntity>): Map<String, Int> = PersonalRanking.changes(newOrder, { it.userRank }, { it.titleKey })
}
