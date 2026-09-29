package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.library.GameCatalog
import com.davidgcd.backlog.data.remote.IgdbApi
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.util.RateLimiter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Thin wrapper over [IgdbApi] that reserves the shared rate-limit slot before
 * every request, the same rule the iOS app's IGDBService.performRequest
 * follows.
 */
class IgdbService(private val api: IgdbApi) : GameCatalog {

    override suspend fun searchGames(query: String, limit: Int): List<Game> =
        searchGames(query, limit, partial = false)

    /**
     * [partial] also matches titles containing every word of the query as a fragment (IGDB's
     * `search` only matches whole words, so "link awak" never finds "Link's Awakening"). Half of
     * the slots keep IGDB's relevance order; the contains-hits fill in right after them.
     */
    suspend fun searchGames(query: String, limit: Int = 20, partial: Boolean): List<Game> {
        val ranked = searchByRelevance(query, limit)
        if (!partial) return ranked
        val tokens = query.split(Regex("[\\s'’:\\-]+")).filter { it.length >= 2 }
        if (tokens.isEmpty()) return ranked
        val contained = searchByNameContains(tokens, limit)
        val head = limit / 2
        return (ranked.take(head) + contained + ranked.drop(head)).distinctBy { it.id }.take(limit)
    }

    private suspend fun searchByNameContains(tokens: List<String>, limit: Int): List<Game> {
        RateLimiter.igdb.acquire()
        // `~ *"x"*` is IGDB's case-insensitive "contains"; version_parent = null hides DLC-ish editions.
        val conditions = tokens.joinToString(" & ") { "name ~ *\"${apicalypseEscaped(it)}\"*" }
        val apicalypse = """
            fields id,name,cover.image_id,first_release_date,genres.name,platforms.name,summary,total_rating;
            where $conditions & version_parent = null;
            sort total_rating_count desc;
            limit $limit;
        """.trimIndent()
        return api.games(apicalypse.toRequestBody()).filter { it.name.isNotBlank() }
    }

    private suspend fun searchByRelevance(query: String, limit: Int): List<Game> {
        RateLimiter.igdb.acquire()
        // IGDB's /search endpoint only accepts id/name/game — every other field, even scalars
        // like first_release_date, comes back "Invalid field name". Its `id` is the *search
        // result's* id, not the game's (a hit can even match a character/company with no game at
        // all) — the actual game id is the `game` field. Get that here, then enrich with a normal
        // /games lookup (which supports the full field set) keyed by the real game ids.
        val searchApicalypse = """
            search "${apicalypseEscaped(query)}";
            fields id,name,game;
            limit $limit;
        """.trimIndent()
        val gameIds = api.search(searchApicalypse.toRequestBody())
            .mapNotNull { it.game }
            .distinct()
        if (gameIds.isEmpty()) return emptyList()

        RateLimiter.igdb.acquire()
        val idsApicalypse = """
            fields id,name,cover.image_id,first_release_date,genres.name,platforms.name,summary,total_rating;
            where id = (${gameIds.joinToString(",")});
            limit ${gameIds.size};
        """.trimIndent()
        val detailsById = api.games(idsApicalypse.toRequestBody()).associateBy { it.id }
        // /games doesn't preserve /search's relevance order, so re-sort to match the search hits,
        // and drop the rare malformed entry with no name at all (parsed as "" — not worth showing).
        return gameIds.mapNotNull { detailsById[it] }.filter { it.name.isNotBlank() }
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
        return api.games(apicalypse.toRequestBody())
    }

    suspend fun getGame(id: Long): Game? {
        RateLimiter.igdb.acquire()
        // websites.category/url is only requested here, never in searchGames — the iOS app's rule of
        // keeping fields used by a single screen out of the shared sync/search query.
        val apicalypse = """
            fields id,name,cover.image_id,first_release_date,genres.name,platforms.name,summary,total_rating,websites.url,websites.category;
            where id = $id;
        """.trimIndent()
        return api.games(apicalypse.toRequestBody()).firstOrNull()
    }

    override suspend fun resolveExternalIds(externalSourceId: Int, externalIds: List<String>): Map<String, Long> {
        val resolved = LinkedHashMap<String, Long>()
        externalIds.chunked(BATCH_SIZE).forEach { chunk ->
            RateLimiter.igdb.acquire()
            val uids = chunk.joinToString(",") { "\"${apicalypseEscaped(it)}\"" }
            val apicalypse = """
                fields uid,game;
                where external_game_source = $externalSourceId & uid = ($uids) & game != null;
                limit ${BATCH_LIMIT};
            """.trimIndent()
            api.externalGames(apicalypse.toRequestBody()).forEach { row ->
                val game = row.game ?: return@forEach
                resolved.putIfAbsent(row.uid, game)
            }
        }
        return resolved
    }

    override suspend fun getGamesByIds(ids: List<Long>): List<Game> {
        val games = mutableListOf<Game>()
        ids.distinct().chunked(BATCH_SIZE).forEach { chunk ->
            RateLimiter.igdb.acquire()
            val apicalypse = """
                fields id,name,cover.image_id,first_release_date,genres.name,platforms.name,summary,total_rating,websites.url,websites.category;
                where id = (${chunk.joinToString(",")});
                limit ${BATCH_LIMIT};
            """.trimIndent()
            games += api.games(apicalypse.toRequestBody()).filter { it.name.isNotBlank() }
        }
        return games
    }

    private companion object {
        const val BATCH_SIZE = 100
        const val BATCH_LIMIT = 500
    }

    /** Escapes a value going into an Apicalypse double-quoted string: backslash before quote. */
    private fun apicalypseEscaped(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun String.toRequestBody() = toRequestBody("text/plain".toMediaType())
}
