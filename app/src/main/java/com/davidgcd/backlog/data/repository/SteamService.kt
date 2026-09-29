package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.remote.SteamApi
import com.davidgcd.backlog.util.AppLogger

data class SteamReviewSummary(
    val percentPositive: Int,
    val totalReviews: Int,
    /** Steam's own wording ("Extrêmement positives", "Mitigées"…), never re-derived — the API localizes it (`l=french`). */
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

    /** French store-page blurb, or null (no Steam page / no French text / service down) — the caller keeps IGDB's summary then. */
    suspend fun frenchDescription(appId: Long): String? {
        return try {
            api.appDetails(appId)[appId.toString()]
                ?.takeIf { it.success }
                ?.data?.shortDescription
                ?.let { unescapeHtml(it) }
                ?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            AppLogger.network.warn("Steam description fetch failed for appId=$appId: ${t.message}")
            null
        }
    }

    // Steam's short_description carries a few HTML entities/tags; the UI shows plain text.
    private fun unescapeHtml(text: String): String =
        text.replace(Regex("<[^>]*>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()

    companion object {
        fun storePageUrl(appId: Long): String = "https://store.steampowered.com/app/$appId"
    }
}
