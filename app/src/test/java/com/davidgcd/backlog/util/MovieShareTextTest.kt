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
            MovieEntity("tt2", "Zoo", year = 2020, status = WatchStatus.TO_WATCH.name),
            MovieEntity("tt1", "Alpha", year = 2019, status = WatchStatus.WATCHED.name, userRank = 1),
            MovieEntity("tt3", "Beta", status = WatchStatus.WATCHED.name),
            MovieEntity("tt4", "Old", status = WatchStatus.WATCHED.name, isArchived = true),
        )
        val expected = """
            Ma liste

            Mon classement
            1. Alpha (2019) — https://www.imdb.com/title/tt1/

            À voir
            - Zoo (2020) — https://www.imdb.com/title/tt2/

            Vu
            - Beta — https://www.imdb.com/title/tt3/
        """.trimIndent()
        assertEquals(expected, MovieShareText.build(movies, labels))
    }
}
