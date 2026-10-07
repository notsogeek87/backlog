package com.davidgcd.backlog.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TonightTest {
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun game(key: String, inProgress: Boolean = false, ageDays: Int = 10, quality: Double? = null) = TonightCandidate(
        Medium.GAME, key, key, inProgress = inProgress, addedAt = now - ageDays * day, quality = quality,
    )

    private fun movie(key: String, minutes: Int) = TonightCandidate(Medium.MOVIE, key, key, addedAt = now - 5 * day, durationMinutes = minutes)

    @Test
    fun `what is already started comes first`() {
        val picks = Tonight.suggest(listOf(game("old", ageDays = 200), game("started", inProgress = true, ageDays = 1)), null, TimeBudget.LONG, now)
        assertEquals("started", picks.first().key)
    }

    @Test
    fun `the medium filter keeps only that medium`() {
        val picks = Tonight.suggest(listOf(game("g"), movie("m", 100)), Medium.MOVIE, TimeBudget.LONG, now)
        assertEquals(listOf("m"), picks.map { it.key })
    }

    @Test
    fun `a film longer than the time budget is left out but a game with no known length stays`() {
        val picks = Tonight.suggest(listOf(movie("long", 170), movie("short", 80), game("g")), null, TimeBudget.SHORT, now)
        assertEquals(setOf("short", "g"), picks.map { it.key }.toSet())
    }

    @Test
    fun `at most the requested count and a different seed can change the order of equals`() {
        val candidates = (1..10).map { game("g$it") }
        assertEquals(3, Tonight.suggest(candidates, null, TimeBudget.EVENING, now).size)
        assertEquals(5, Tonight.suggest(candidates, null, TimeBudget.EVENING, now, count = 5).size)
        val a = Tonight.suggest(candidates, null, TimeBudget.EVENING, now, seed = 1).map { it.key }
        val b = Tonight.suggest(candidates, null, TimeBudget.EVENING, now, seed = 1).map { it.key }
        assertEquals("same seed, same picks", a, b)
    }

    @Test
    fun `reading time is derived from the page count`() {
        assertEquals(300, Tonight.readingMinutes(200))
        assertTrue(Tonight.readingMinutes(null) == null && Tonight.readingMinutes(0) == null)
    }

    @Test
    fun `with every medium selected the picks alternate instead of being all games`() {
        val games = (1..6).map { game("g$it", inProgress = true) }
        val picks = Tonight.suggest(games + movie("m", 100) + TonightCandidate(Medium.BOOK, "b", "b", addedAt = now - 5 * day), null, TimeBudget.LONG, now)
        assertEquals(setOf(Medium.GAME, Medium.MOVIE, Medium.BOOK), picks.map { it.medium }.toSet())
    }
}
