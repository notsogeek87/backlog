package com.davidgcd.backlog.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * IGDB runs on Twitch's API and needs an app access token (client-credentials grant). The grant needs the
 * Twitch client SECRET, which must never ship in the APK: the app's server (`server/api/igdb-token.js`)
 * holds it and hands out the token, plus the public client id IGDB also expects.
 */
data class IgdbTokenResponse(
    val client_id: String,
    val access_token: String,
    val expires_in: Long,
)

interface IgdbTokenApi {
    /** [stale] = [IgdbTokenProvider.fingerprint] of a token IGDB just refused, so the server renews it. */
    @GET("api/igdb-token")
    suspend fun token(@Query("stale") stale: String? = null): IgdbTokenResponse
}
