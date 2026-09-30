package com.davidgcd.backlog.util

import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MovieShareTextTest {
    private val labels = MovieShareText.Labels(
        header = { "Ma liste" },
        status = { s -> when (s) { WatchStatus.TO_WATCH -> "À voir"; WatchStatus.WATCHING -> "En cours"; WatchStatus.WATCHED -> "Vu" } },
        ranking = "Mon classement",
    )

    @Test
    fun `ranking first then statuses, archived left out`() {
        val movies = listOf(
            MovieEntity("movie:2", "Zoo", year = 2020, status = WatchStatus.TO_WATCH.name),
            MovieEntity("tv:1", "Alpha", year = 2019, status = WatchStatus.WATCHED.name, userRank = 1),
            MovieEntity("movie:3", "Beta", status = WatchStatus.WATCHED.name),
            MovieEntity("movie:4", "Old", status = WatchStatus.WATCHED.name, isArchived = true),
        )
        val expected = """
            Ma liste

            Mon classement
            1. Alpha (2019) — https://www.themoviedb.org/tv/1

            À voir
            - Zoo (2020) — https://www.themoviedb.org/movie/2

            Vu
            - Beta — https://www.themoviedb.org/movie/3
        """.trimIndent()
        assertEquals(expected, MovieShareText.build(movies, labels))
    }

    @Test
    fun `inside a status, best user note first and the note is shown`() {
        val movies = listOf(
            MovieEntity("movie:1", "Alpha", status = WatchStatus.WATCHED.name, userRating = 6),
            MovieEntity("movie:2", "Beta", status = WatchStatus.WATCHED.name),
            MovieEntity("movie:3", "Zulu", status = WatchStatus.WATCHED.name, userRating = 9),
        )
        val expected = """
            Ma liste

            Vu
            - Zulu — 9/10 — https://www.themoviedb.org/movie/3
            - Alpha — 6/10 — https://www.themoviedb.org/movie/1
            - Beta — https://www.themoviedb.org/movie/2
        """.trimIndent()
        assertEquals(expected, MovieShareText.build(movies, labels))
    }
}
