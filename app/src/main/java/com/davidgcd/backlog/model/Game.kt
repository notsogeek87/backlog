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

@JsonClass(generateAdapter = true)
data class Website(
    val id: Long,
    val url: String,
    val category: Int,
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
