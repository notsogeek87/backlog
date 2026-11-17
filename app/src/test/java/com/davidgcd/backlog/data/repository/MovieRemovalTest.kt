package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.tmdb.TmdbListContent
import com.davidgcd.backlog.model.MediaTitle
import com.davidgcd.backlog.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MovieRemovalTest {
    private val index = listOf(
        TmdbListContent(TmdbListContent.WATCHLIST_ID, "", setOf("movie:603")),
        TmdbListContent(8001, "Charlie", setOf("movie:603", "movie:1")),
        TmdbListContent(8002, "Autre", setOf("movie:1")),
    )

    @Test fun aRemovedTitleLeavesEveryTmdbListHoldingIt() {
        val movie = MediaTitle("movie:603", "Matrix").toEntity(WatchStatus.WATCHED)
        assertEquals(setOf(TmdbListContent.WATCHLIST_ID, 8001L), membershipsToClear(movie, index))
    }

    @Test fun aToWatchTitleLeavesTheWatchlistEvenBeforeTheIndexIsKnown() {
        val movie = MediaTitle("movie:42", "X").toEntity(WatchStatus.TO_WATCH)
        assertEquals(setOf(TmdbListContent.WATCHLIST_ID), membershipsToClear(movie, emptyList()))
    }

    @Test fun aWatchedTitleInNoListTouchesNothing() {
        val movie = MediaTitle("movie:42", "X").toEntity(WatchStatus.WATCHED)
        assertEquals(emptySet<Long>(), membershipsToClear(movie, index))
    }
}
