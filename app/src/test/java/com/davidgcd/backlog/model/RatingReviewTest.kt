package com.davidgcd.backlog.model

import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.MovieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RatingReviewTest {
    private fun game(id: Long, ratedAt: Long?, archived: Boolean = false) =
        GameEntity(igdbId = id, name = "g$id", userRating = 7, ratedAt = ratedAt, isArchived = archived)

    @Test
    fun `la file va de la note la plus ancienne a la plus recente, tous medias confondus`() {
        val queue = RatingReview.queue(
            games = listOf(game(1, ratedAt = 300)),
            movies = listOf(MovieEntity(titleKey = "movie:1", title = "m", userRating = 8, ratedAt = 100)),
            books = listOf(BookEntity(bookKey = "b1", title = "b", userRating = 4, ratedAt = 200)),
        )
        assertEquals(listOf("movie:1", "b1", "1"), queue.map { it.key })
    }

    @Test
    fun `ce qui n'est pas date passe avant les notes datees`() {
        val queue = RatingReview.queue(
            games = listOf(game(1, ratedAt = 50), game(2, ratedAt = null), game(3, ratedAt = null)),
            movies = emptyList(),
            books = emptyList(),
        )
        assertEquals("1", queue.last().key)
        assertEquals(setOf("2", "3"), queue.take(2).map { it.key }.toSet())
    }

    @Test
    fun `les items non dates sont melanges au hasard`() {
        val games = (1L..30L).map { game(it, ratedAt = null) }
        val first = RatingReview.queue(games, emptyList(), emptyList(), Random(1)).map { it.key }
        val second = RatingReview.queue(games, emptyList(), emptyList(), Random(2)).map { it.key }
        assertEquals(first.sorted(), second.sorted())
        assertFalse(first == second)
        assertEquals(30, first.size)
    }

    @Test
    fun `les jeux et films archives sont ignores, les livres gardent leur echelle sur 5`() {
        val queue = RatingReview.queue(
            games = listOf(game(1, null), game(2, null, archived = true)),
            movies = listOf(MovieEntity(titleKey = "movie:2", title = "x", isArchived = true)),
            books = listOf(BookEntity(bookKey = "b", title = "livre")),
        )
        assertEquals(setOf("1", "b"), queue.map { it.key }.toSet())
        assertEquals(5, queue.first { it.medium == Medium.BOOK }.maxRating)
        assertTrue(queue.first { it.medium == Medium.GAME }.maxRating == 10)
    }
}
