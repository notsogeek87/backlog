package com.davidgcd.backlog.model

import com.davidgcd.backlog.data.local.GameEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RankingTest {
    private fun game(id: Long, rank: Int? = null, added: Long = id, archived: Boolean = false) =
        GameEntity(igdbId = id, name = "g$id", userRank = rank, addedAt = added, isArchived = archived)

    @Test
    fun rankedFirstThenUnrankedByAddedDateAndArchivedLeft() {
        val order = Ranking.order(listOf(game(1), game(2, rank = 2), game(3, rank = 1), game(4, archived = true, rank = 1)))
        assertEquals(listOf(3L, 2L, 1L), order.map { it.igdbId })
    }

    @Test
    fun moveSwapsNeighboursAndIgnoresTheEnds() {
        val list = listOf(game(1, 1), game(2, 2), game(3, 3))
        assertEquals(listOf(2L, 1L, 3L), Ranking.move(list, 1, -1).map { it.igdbId })
        assertEquals(list, Ranking.move(list, 0, -1))
        assertEquals(list, Ranking.move(list, 2, 1))
    }

    @Test
    fun changesOnlyListsGamesWhoseRankMoves() {
        val moved = Ranking.move(listOf(game(1, 1), game(2, 2), game(3, 3)), 2, -1)
        assertEquals(mapOf(3L to 2, 2L to 3), Ranking.changes(moved))
    }

    @Test
    fun firstMoveRanksEveryoneWhenNothingWasRankedYet() {
        val moved = Ranking.move(Ranking.order(listOf(game(1), game(2), game(3))), 2, -1)
        assertEquals(mapOf(1L to 1, 3L to 2, 2L to 3), Ranking.changes(moved))
    }

    @Test
    fun moveToJumpsStraightToTheTopOrBottom() {
        val list = listOf(game(1, 1), game(2, 2), game(3, 3), game(4, 4))
        assertEquals(listOf(4L, 1L, 2L, 3L), Ranking.moveTo(list, 3, 0).map { it.igdbId })
        assertEquals(listOf(2L, 3L, 4L, 1L), Ranking.moveTo(list, 0, 3).map { it.igdbId })
        assertEquals(list, Ranking.moveTo(list, 0, 0))
    }

    @Test
    fun jumpToTopRewritesEveryRankItPasses() {
        val moved = Ranking.moveTo(listOf(game(1, 1), game(2, 2), game(3, 3)), 2, 0)
        assertEquals(mapOf(3L to 1, 1L to 2, 2L to 3), Ranking.changes(moved))
    }
}
