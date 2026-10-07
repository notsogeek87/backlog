package com.davidgcd.backlog.model

import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.MovieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class YearRecapTest {
    private val zone = ZoneOffset.UTC
    private fun millis(y: Int, m: Int = 6, d: Int = 15) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `counts what was finished in the year using the completion date`() {
        val games = listOf(
            GameEntity(1, "A", status = GameStatus.COMPLETED.name, addedAt = millis(2023), completedAt = millis(2025)),
            GameEntity(2, "B", status = GameStatus.COMPLETED.name, addedAt = millis(2025), completedAt = millis(2024)),
            GameEntity(3, "C", status = GameStatus.PLAYED.name, addedAt = millis(2025)),
        )
        val recap = YearRecaps.compute(2025, games, emptyList(), emptyList(), zone)
        assertEquals(1, recap.gamesFinished)
        assertEquals(2, recap.added)
    }

    @Test
    fun `a finished item with no completion date falls back to the year it was added`() {
        val movies = listOf(MovieEntity("movie:1", "M", status = WatchStatus.WATCHED.name, addedAt = millis(2025), runtimeMinutes = 120))
        val recap = YearRecaps.compute(2025, emptyList(), movies, emptyList(), zone)
        assertEquals(1, recap.moviesWatched)
        assertEquals(120, recap.minutesWatched)
    }

    @Test
    fun `books add their pages and the best rated finished items become the picks`() {
        val books = listOf(BookEntity("b1", "Livre", status = ReadStatus.READ.name, pageCount = 300, userRating = 5, addedAt = millis(2025)))
        val movies = listOf(MovieEntity("movie:2", "Film", status = WatchStatus.WATCHED.name, userRating = 6, addedAt = millis(2025)))
        val recap = YearRecaps.compute(2025, emptyList(), movies, books, zone)
        assertEquals(300, recap.pagesRead)
        assertEquals(listOf("Livre", "Film"), recap.picks.map { it.title })
    }

    @Test
    fun `years lists every year with activity newest first and an empty year is empty`() {
        val games = listOf(GameEntity(1, "A", addedAt = millis(2022)), GameEntity(2, "B", addedAt = millis(2025)))
        assertEquals(listOf(2025, 2022), YearRecaps.years(games, emptyList(), emptyList(), zone))
        assertTrue(YearRecaps.compute(2019, games, emptyList(), emptyList(), zone).isEmpty)
    }
}
