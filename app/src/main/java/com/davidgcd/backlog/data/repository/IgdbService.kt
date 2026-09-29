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
        // IGDB's fuzzy search endpoint can return several matches (e.g. alternative names)
        // sharing the same game id, which breaks Compose's LazyColumn key requirement, and
        // occasionally a malformed entry with no name at all (parsed as "" — not worth showing).
        return api.search(apicalypse).distinctBy { it.id }.filter { it.name.isNotBlank() }
    }

    /** Popular-enough games with a real rating, most-rated first — a simple stand-in for the iOS app's PopularGamesLoader/RRF fusion. */
    suspend fun getPopularGames(limit: Int = 20): List<Game> {
        RateLimiter.igdb.acquire()
        val apicalypse = """
            fields id,name,cover.image_id,first_release_date,genres.name,platforms.name,summary,total_rating;
            where total_rating_count > 10 & cover != null;
            sort total_rating_count desc;
            limit $limit;
        """.trimIndent()
        return api.games(apicalypse)
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
