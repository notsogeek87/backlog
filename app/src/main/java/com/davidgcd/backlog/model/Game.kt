package com.davidgcd.backlog.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * IGDB API response shape. Kept separate from [com.davidgcd.backlog.data.local.GameEntity]
 * (the persisted Room row), the same split as Game/GameEntity on the iOS app.
 */
@JsonClass(generateAdapter = true)
data class Game(
    val id: Long,
    // IGDB occasionally omits `name` entirely on malformed/placeholder entries even when explicitly
    // requested — default instead of failing the whole response's parse for one bad entry.
    val name: String = "",
    @Json(name = "cover") val cover: Cover? = null,
    @Json(name = "first_release_date") val firstReleaseDate: Long? = null,
    @Json(name = "genres") val genres: List<Genre>? = null,
    @Json(name = "platforms") val platforms: List<Platform>? = null,
    @Json(name = "summary") val summary: String? = null,
    @Json(name = "total_rating") val totalRating: Double? = null,
    @Json(name = "websites") val websites: List<Website>? = null,
) {
    /**
     * Extracted from the IGDB `websites` expander (category 13 = Steam),
     * never hand-entered — mirrors the iOS app's own Steam App ID
     * extraction from `websites` in GameDetailView.
     */
    val steamAppId: Long?
        get() = websites
            ?.firstOrNull { it.category == STEAM_WEBSITE_CATEGORY }
            ?.url
            ?.let { STEAM_APP_URL_REGEX.find(it)?.groupValues?.get(1)?.toLongOrNull() }

    private companion object {
        const val STEAM_WEBSITE_CATEGORY = 13
        val STEAM_APP_URL_REGEX = Regex("""/app/(\d+)""")
    }
}

/**
 * IGDB's /search endpoint returns its own result objects, not [Game]s directly: `id` here is the
 * search *result's* id, and `game` is the actual game's id (the one /games expects) — a distinct
 * result can even have no `game` at all (a match on a character/company/collection instead).
 */
@JsonClass(generateAdapter = true)
data class SearchHit(
    val id: Long,
    val name: String = "",
    val game: Long? = null,
)

/** IGDB `external_games` row: [uid] is the store's own id (a Steam AppID as text), [game] the IGDB game id. */
@JsonClass(generateAdapter = true)
data class ExternalGame(
    val uid: String = "",
    val game: Long? = null,
)

@JsonClass(generateAdapter = true)
data class Website(
    val id: Long,
    // IGDB occasionally omits url/category on a website entry — default rather than fail the
    // whole game's parse for one bad entry (mirrors Game.name/SearchHit.name above).
    val url: String = "",
    val category: Int? = null,
)

@JsonClass(generateAdapter = true)
data class Cover(
    @Json(name = "image_id") val imageId: String?,
)

@JsonClass(generateAdapter = true)
data class Genre(
    val id: Long,
    val name: String,
)

@JsonClass(generateAdapter = true)
data class Platform(
    val id: Long,
    val name: String,
)
