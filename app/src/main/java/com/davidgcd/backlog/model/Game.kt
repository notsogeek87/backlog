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
    val name: String,
    @Json(name = "cover") val cover: Cover? = null,
    @Json(name = "first_release_date") val firstReleaseDate: Long? = null,
    @Json(name = "genres") val genres: List<Genre>? = null,
    @Json(name = "platforms") val platforms: List<Platform>? = null,
    @Json(name = "summary") val summary: String? = null,
    @Json(name = "total_rating") val totalRating: Double? = null,
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
