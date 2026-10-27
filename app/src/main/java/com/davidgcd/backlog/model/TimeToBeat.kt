package com.davidgcd.backlog.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * IGDB `game_time_to_beats` row: how long players take to finish a game, in seconds. HowLongToBeat has
 * no public API, so this is the official equivalent (same token as the rest of IGDB, no scraping).
 * Each figure is optional — IGDB often knows only some of them.
 */
@JsonClass(generateAdapter = true)
data class TimeToBeat(
    /** Histoire principale en ligne droite. */
    @Json(name = "hastily") val hastily: Long? = null,
    /** Histoire principale + quelques extras (la valeur « normale »). */
    @Json(name = "normally") val normally: Long? = null,
    /** Complétiste : tout faire. */
    @Json(name = "completely") val completely: Long? = null,
    /** Number of player submissions behind the figures. */
    @Json(name = "count") val count: Int? = null,
) {
    /** False when IGDB has no usable figure — the section is then hidden. */
    val hasData: Boolean
        get() = listOf(hastily, normally, completely).any { it != null && it > 0 }

    companion object {
        /** Picks the row with the most submissions; ignores rows without any figure. */
        fun best(rows: List<TimeToBeat>): TimeToBeat? =
            rows.filter { it.hasData }.maxByOrNull { it.count ?: 0 }
    }
}
