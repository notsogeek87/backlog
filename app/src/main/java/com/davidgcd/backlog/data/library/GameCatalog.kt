package com.davidgcd.backlog.data.library

import com.davidgcd.backlog.model.Game

/** The slice of IGDB the library sync needs — an interface so tests don't have to speak Apicalypse. */
interface GameCatalog {
    /** [externalIds] (a store's own ids) → IGDB game id. Ids IGDB doesn't know are simply absent. */
    suspend fun resolveExternalIds(externalSourceId: Int, externalIds: List<String>): Map<String, Long>

    suspend fun getGamesByIds(ids: List<Long>): List<Game>

    suspend fun searchGames(query: String, limit: Int = 20): List<Game>
}
