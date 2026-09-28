package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.remote.IgdbApi
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.util.RateLimiter

/**
 * Thin wrapper over [IgdbApi] that reserves the shared rate-limit slot before
 * every request, the same rule the iOS app's IGDBService.performRequest
 * follows.
 */
class IgdbService(private val api: IgdbApi) {

    suspend fun searchGames(query: String, limit: Int = 20): List<Game> {
        RateLimiter.igdb.acquire()
        val apicalypse = """
            search "${apicalypseEscaped(query)}";
            fields id,name,cover.image_id,first_release_date,genres.name,platforms.name,summary,total_rating;
            limit $limit;
        """.trimIndent()
        return api.search(apicalypse)
    }

    suspend fun getGame(id: Long): Game? {
        RateLimiter.igdb.acquire()
        // websites.category/url is only requested here, never in searchGames — the iOS app's rule of
        // keeping fields used by a single screen out of the shared sync/search query.
        val apicalypse = """
            fields id,name,cover.image_id,first_release_date,genres.name,platforms.name,summary,total_rating,websites.url,websites.category;
            where id = $id;
        """.trimIndent()
        return api.games(apicalypse).firstOrNull()
    }

    /** Escapes a value going into an Apicalypse double-quoted string: backslash before quote. */
    private fun apicalypseEscaped(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
