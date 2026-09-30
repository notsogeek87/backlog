package com.davidgcd.backlog.model

import com.davidgcd.backlog.data.local.MovieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MovieRankingTest {
    private fun movie(id: String, rank: Int? = null, added: Long = 0, archived: Boolean = false) =
        MovieEntity(imdbId = id, title = id, userRank = rank, addedAt = added, isArchived = archived)

    @Test
    fun `ranked first, then unranked by date, archived never`() {
        val list = listOf(
            movie("a", added = 3),
            movie("b", rank = 2, added = 9),
            movie("c", rank = 1, added = 8),
            movie("d", added = 1),
            movie("e", rank = 1, added = 1, archived = true),
        )
        assertEquals(listOf("c", "b", "d", "a"), MovieRanking.order(list).map { it.imdbId })
    }

    @Test
    fun `move and changes`() {
        val ordered = listOf(movie("a", 1), movie("b", 2), movie("c", 3))
        val moved = MovieRanking.move(ordered, 2, -1)
        assertEquals(listOf("a", "c", "b"), moved.map { it.imdbId })
        assertEquals(mapOf("c" to 2, "b" to 3), MovieRanking.changes(moved))
        assertSame(ordered, MovieRanking.move(ordered, 0, -1))
        assertEquals(listOf("c", "a", "b"), MovieRanking.moveTo(ordered, 2, 0).map { it.imdbId })
    }

    @Test
    fun `unranked titles get their rank on first move`() {
        val ordered = listOf(movie("a"), movie("b"))
        assertEquals(mapOf("a" to 1, "b" to 2), MovieRanking.changes(ordered))
    }
}
