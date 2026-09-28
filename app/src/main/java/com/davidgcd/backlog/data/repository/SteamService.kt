package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.remote.SteamApi
import com.davidgcd.backlog.util.AppLogger

data class SteamReviewSummary(
    val percentPositive: Int,
    val totalReviews: Int,
    /** Steam's own wording ("Overwhelmingly Positive", "Mixed"…), never re-derived — the API already localizes it. */
    val verdict: String?,
)

/**
 * Free, no API key needed — Steam's `appreviews` endpoint is public. Never
 * throws: like the iOS app's HowLongToBeat/Metacritic sections, "the
 * service is down" and "this game has no reviews" both just mean no card,
 * no error message, no retry button.
 */
class SteamService(private val api: SteamApi) {

    suspend fun reviewSummary(appId: Long): SteamReviewSummary? {
        return try {
            val summary = api.reviews(appId).querySummary ?: return null
            if (summary.totalReviews == 0) return null
            val percent = (summary.totalPositive * 100.0 / summary.totalReviews).let { Math.round(it).toInt() }
            SteamReviewSummary(
                percentPositive = percent,
                totalReviews = summary.totalReviews,
                // review_score 0 means "too few reviews for a verdict" — same rule as the iOS app.
                verdict = summary.reviewScoreDesc.takeIf { summary.reviewScore != 0 },
            )
        } catch (t: Throwable) {
            AppLogger.network.warn("Steam review fetch failed for appId=$appId: ${t.message}")
            null
        }
    }

    companion object {
        fun storePageUrl(appId: Long): String = "https://store.steampowered.com/app/$appId"
    }
}
