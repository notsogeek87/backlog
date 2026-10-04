package com.davidgcd.backlog.data.remote

import com.davidgcd.backlog.model.ExternalGame
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.model.GameVideo
import com.davidgcd.backlog.model.SearchHit
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * IGDB uses a single POST endpoint per resource with an Apicalypse query body,
 * not REST query params. Client-Id / Authorization headers are added by
 * [IgdbAuthInterceptor], never hardcoded per call.
 *
 * The Apicalypse body is sent as a raw [RequestBody] (built with a text/plain media
 * type by the caller), not a plain Kotlin String: Retrofit would otherwise hand a
 * String @Body to the Moshi converter, which JSON-encodes it (wrapping it in quotes
 * and escaping its newlines) instead of sending it as literal text — IGDB then
 * silently ignores everything past the mangled `fields` clause.
 */
interface IgdbApi {
    @POST("v4/games")
    suspend fun games(@Body apicalypseQuery: RequestBody): List<Game>

    @POST("v4/search")
    suspend fun search(@Body apicalypseQuery: RequestBody): List<SearchHit>

    @POST("v4/game_videos")
    suspend fun gameVideos(@Body apicalypseQuery: RequestBody): List<GameVideo>

    /** Maps a store's own ids (Steam AppID…) to IGDB game ids. */
    @POST("v4/external_games")
    suspend fun externalGames(@Body apicalypseQuery: RequestBody): List<ExternalGame>
}
