package com.davidgcd.backlog.ui.backlog

import com.davidgcd.backlog.data.remote.IgdbApi
import com.davidgcd.backlog.model.Game

/** Never called by the sort/filter tests, but BacklogRepository needs a real IgdbService to construct. */
class FakeIgdbApi(private val results: List<Game> = emptyList()) : IgdbApi {
    override suspend fun games(apicalypseQuery: String): List<Game> = results

    override suspend fun search(apicalypseQuery: String): List<Game> = results
}
