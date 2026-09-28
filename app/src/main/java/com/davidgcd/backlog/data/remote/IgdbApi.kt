package com.davidgcd.backlog.data.remote

import com.davidgcd.backlog.model.Game
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * IGDB uses a single POST endpoint per resource with an Apicalypse query body,
 * not REST query params. Client-Id / Authorization headers are added by
 * [IgdbAuthInterceptor], never hardcoded per call.
 */
interface IgdbApi {
    @Headers("Content-Type: text/plain")
    @POST("v4/games")
    suspend fun games(@Body apicalypseQuery: String): List<Game>

    @Headers("Content-Type: text/plain")
    @POST("v4/search")
    suspend fun search(@Body apicalypseQuery: String): List<Game>
}
