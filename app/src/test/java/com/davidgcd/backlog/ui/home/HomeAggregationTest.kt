package com.davidgcd.backlog.ui.home

import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.model.Medium
import com.davidgcd.backlog.model.ReadStatus
import com.davidgcd.backlog.model.TitleKind
import com.davidgcd.backlog.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAggregationTest {
    private val now = 1_700_000_000_000L
    private val nowSeconds = now / 1000
    private val day = 86_400L

    @Test
    fun `an empty library is flagged empty and shows nothing`() {
        val state = HomeViewModel.build(emptyList(), emptyList(), emptyList(), now)
        assertTrue(state.isEmpty)
        assertTrue(state.inProgress.isEmpty() && state.upcoming.isEmpty() && state.recent.isEmpty())
    }

    @Test
    fun `in progress gathers played games, films being watched and books being read, archived excluded`() {
        val games = listOf(
            GameEntity(1, "Joué", status = GameStatus.PLAYED.name, addedAt = 3),
            GameEntity(2, "Backlog", status = GameStatus.BACKLOG.name, addedAt = 2),
            GameEntity(3, "Archivé", status = GameStatus.PLAYED.name, isArchived = true, addedAt = 1),
        )
        val movies = listOf(MovieEntity("tv:1", "Série", kind = TitleKind.SERIES.name, status = WatchStatus.WATCHING.name, addedAt = 5))
        val books = listOf(BookEntity("b1", "Livre", status = ReadStatus.READING.name, addedAt = 4))
        val state = HomeViewModel.build(games, movies, books, now)
        assertEquals(listOf("Série", "Livre", "Joué"), state.inProgress.map { it.title })
        assertFalse(state.isEmpty)
    }

    @Test
    fun `upcoming keeps releases from today to thirty days, soonest first, and drops finished ones`() {
        val dayStart = nowSeconds - nowSeconds % day
        val games = listOf(
            GameEntity(1, "Dans 10 jours", firstReleaseDate = nowSeconds + 10 * day),
            GameEntity(2, "Aujourd'hui", firstReleaseDate = dayStart + 3600),
            GameEntity(3, "Dans 60 jours", firstReleaseDate = nowSeconds + 60 * day),
            GameEntity(4, "Déjà sorti", firstReleaseDate = nowSeconds - 5 * day),
            GameEntity(5, "Terminé", status = GameStatus.COMPLETED.name, firstReleaseDate = nowSeconds + 5 * day),
        )
        val movies = listOf(MovieEntity("movie:1", "Film bientôt", releaseDate = nowSeconds + 3 * day))
        val upcoming = HomeViewModel.build(games, movies, emptyList(), now).upcoming
        assertEquals(listOf("Aujourd'hui", "Film bientôt", "Dans 10 jours"), upcoming.map { it.item.title })
        assertEquals(Medium.MOVIE, upcoming[1].item.medium)
    }
}
