package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.remote.MetacriticApi
import com.davidgcd.backlog.util.AppLogger
import com.davidgcd.backlog.util.TitleSimilarity

data class MetacriticScore(val score: Int, val url: String?)

/**
 * Scaffold, not a finished integration: Metacritic has no free official API,
 * so [MetacriticApi] targets whatever RapidAPI provider you configure in
 * Secrets.kt (config/Secrets.kt.example explains how). Until real
 * credentials + a matching endpoint contract are in place, every call here
 * either throws (caught below) or returns results that won't title-match —
 * either way this returns null and the score section simply doesn't render,
 * the same "no error, no card" rule the iOS app uses for HowLongToBeat and
 * Metacritic: a missing score is never surfaced as an error.
 */
class MetacriticService(private val api: MetacriticApi) {

    suspend fun scoreFor(gameName: String): MetacriticScore? {
        return try {
            val results = api.search(gameName)
            val best = results
                .filter { it.metascore != null }
                .maxByOrNull { TitleSimilarity.similarity(it.title, gameName) }
                ?.takeIf { TitleSimilarity.matches(it.title, gameName) }
                ?: return null
            MetacriticScore(score = best.metascore!!, url = best.url)
        } catch (t: Throwable) {
            AppLogger.network.warn("Metacritic lookup failed for \"$gameName\": ${t.message}")
            null
        }
    }
}
