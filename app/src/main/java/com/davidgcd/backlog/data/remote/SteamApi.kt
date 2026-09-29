package com.davidgcd.backlog.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SteamApi {
    /**
     * Steam's own default (`purchase_type=steam`) only counts reviews from
     * players who bought the game on Steam, so a free-to-play title reports
     * `total_reviews: 0` and a paid one's total doesn't match the store page
     * — the same discovery the iOS app's SteamService.reviewsURL is built
     * around. `purchase_type=all` is passed explicitly, never left to the
     * default, and frozen here the same way.
     */
    @GET("appreviews/{appId}")
    suspend fun reviews(
        @Path("appId") appId: Long,
        @Query("json") json: Int = 1,
        @Query("purchase_type") purchaseType: String = "all",
        @Query("language") language: String = "all",
        @Query("num_per_page") numPerPage: Int = 1,
        // `language` filters the reviews' own language; `l` is the UI language of Steam's wording
        // (review_score_desc: "Extrêmement positives"…). The app is French-only, so ask for it.
        @Query("l") uiLanguage: String = "french",
    ): SteamReviewsResponse

    /** Store-page data localized by `l` — only used for the French `short_description` (IGDB's summary is English-only). */
    @GET("api/appdetails")
    suspend fun appDetails(
        @Query("appids") appId: Long,
        @Query("l") uiLanguage: String = "french",
    ): Map<String, SteamAppDetailsEntry>
}

@JsonClass(generateAdapter = true)
data class SteamAppDetailsEntry(
    val success: Boolean = false,
    val data: SteamAppDetailsData? = null,
)

@JsonClass(generateAdapter = true)
data class SteamAppDetailsData(
    @Json(name = "short_description") val shortDescription: String? = null,
)

@JsonClass(generateAdapter = true)
data class SteamReviewsResponse(
    val success: Int,
    @Json(name = "query_summary") val querySummary: SteamQuerySummary?,
)

@JsonClass(generateAdapter = true)
data class SteamQuerySummary(
    @Json(name = "review_score") val reviewScore: Int,
    @Json(name = "review_score_desc") val reviewScoreDesc: String,
    @Json(name = "total_positive") val totalPositive: Int,
    @Json(name = "total_negative") val totalNegative: Int,
    @Json(name = "total_reviews") val totalReviews: Int,
)
