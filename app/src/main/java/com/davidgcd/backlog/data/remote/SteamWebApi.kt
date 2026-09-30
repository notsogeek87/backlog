package com.davidgcd.backlog.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Steam *Web API* (api.steampowered.com) — needs an API key, added by [SteamApiKeyInterceptor] so it
 * never appears in a call site (and never reaches a log line: the logging interceptor is installed
 * before it). Distinct from [SteamApi], the keyless store-front reviews endpoint.
 */
interface SteamWebApi {
    @GET("IPlayerService/GetOwnedGames/v1/")
    suspend fun ownedGames(
        @Query("steamid") steamId: String,
        @Query("include_appinfo") includeAppInfo: Int = 1,
        // Free-to-play titles the user actually launched are part of "their library" too.
        @Query("include_played_free_games") includePlayedFreeGames: Int = 1,
        @Query("format") format: String = "json",
    ): OwnedGamesEnvelope

    /** Public wishlist (appid + priority + date added, no names). Empty `response` for a private profile. */
    @GET("IWishlistService/GetWishlist/v1/")
    suspend fun wishlist(
        @Query("steamid") steamId: String,
        @Query("format") format: String = "json",
    ): WishlistEnvelope

    @GET("ISteamUser/GetPlayerSummaries/v2/")
    suspend fun playerSummaries(
        @Query("steamids") steamIds: String,
        @Query("format") format: String = "json",
    ): PlayerSummariesEnvelope
}

/** Steam's OpenID 2.0 endpoint on steamcommunity.com — only used for the server-to-server `check_authentication` call. */
interface SteamOpenIdApi {
    @FormUrlEncoded
    @POST("openid/login")
    suspend fun checkAuthentication(@FieldMap params: Map<String, String>): ResponseBody
}

@JsonClass(generateAdapter = true)
data class OwnedGamesEnvelope(val response: OwnedGamesResponse? = null)

/**
 * Steam answers HTTP 200 with an *empty* `response` object when the profile's game details are
 * private, and with `game_count: 0` when the library is genuinely empty — hence both nullable.
 */
@JsonClass(generateAdapter = true)
data class OwnedGamesResponse(
    @Json(name = "game_count") val gameCount: Int? = null,
    val games: List<OwnedGame>? = null,
)

@JsonClass(generateAdapter = true)
data class OwnedGame(
    val appid: Long,
    val name: String? = null,
    @Json(name = "playtime_forever") val playtimeForever: Int? = null,
    @Json(name = "playtime_2weeks") val playtime2Weeks: Int? = null,
)

@JsonClass(generateAdapter = true)
data class WishlistEnvelope(val response: WishlistResponse? = null)

@JsonClass(generateAdapter = true)
data class WishlistResponse(val items: List<WishlistItem>? = null)

@JsonClass(generateAdapter = true)
data class WishlistItem(val appid: Long)

@JsonClass(generateAdapter = true)
data class PlayerSummariesEnvelope(val response: PlayersResponse? = null)

@JsonClass(generateAdapter = true)
data class PlayersResponse(val players: List<SteamPlayer>? = null)

@JsonClass(generateAdapter = true)
data class SteamPlayer(
    val steamid: String,
    val personaname: String? = null,
    val communityvisibilitystate: Int? = null,
)

/** Appends `key=` to every request. A blank key (e.g. a self-hosted proxy holds it instead) adds nothing. */
class SteamApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (apiKey.isBlank()) return chain.proceed(request)
        val url = request.url.newBuilder().addQueryParameter("key", apiKey).build()
        return chain.proceed(request.newBuilder().url(url).build())
    }
}
