package com.davidgcd.backlog.ui.backlog

import com.davidgcd.backlog.data.remote.IgdbApi
import com.davidgcd.backlog.model.ExternalGame
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.model.SearchHit
import okhttp3.RequestBody

/** Never called by the sort/filter tests, but BacklogRepository needs a real IgdbService to construct. */
class FakeIgdbApi(private val results: List<Game> = emptyList()) : IgdbApi {
    override suspend fun games(apicalypseQuery: RequestBody): List<Game> = results

    override suspend fun search(apicalypseQuery: RequestBody): List<SearchHit> = emptyList()

    override suspend fun externalGames(apicalypseQuery: RequestBody): List<ExternalGame> = emptyList()
}
