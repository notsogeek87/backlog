package com.davidgcd.backlog.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.POST
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class TwitchTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val token_type: String,
)

interface TwitchAuthApi {
    @POST("oauth2/token")
    suspend fun requestToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
        @Query("grant_type") grantType: String = "client_credentials",
    ): TwitchTokenResponse
}
