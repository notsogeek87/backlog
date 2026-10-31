package com.davidgcd.backlog.model

import com.davidgcd.backlog.data.local.GameEntity

/** The personal ranking of games ([PersonalRanking] rules). Archived games never take part. */
object Ranking {
    fun order(games: List<GameEntity>): List<GameEntity> =
        PersonalRanking.order(games.filter { !it.isArchived }, { it.userRank }, { it.addedAt })

    fun move(ordered: List<GameEntity>, index: Int, delta: Int): List<GameEntity> = PersonalRanking.move(ordered, index, delta)

    fun moveTo(ordered: List<GameEntity>, index: Int, target: Int): List<GameEntity> = PersonalRanking.moveTo(ordered, index, target)

    fun changes(newOrder: List<GameEntity>): Map<Long, Int> = PersonalRanking.changes(newOrder, { it.userRank }, { it.igdbId })
}
