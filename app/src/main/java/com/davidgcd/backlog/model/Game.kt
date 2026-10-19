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
    /** The game's igdb.com page; only requested by the share query, absent everywhere else. */
    val url: String? = null,
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

    /** Store pages found in the IGDB `websites`, Steam first, then the other stores in a fixed order. */
    val storeLinks: List<StoreLink>
        get() = websites.orEmpty()
            .mapNotNull { site -> StoreLink.Store.forCategory(site.category)?.let { StoreLink(it, site.url) } }
            .filter { it.url.startsWith("http") }
            .distinctBy { it.store }
            .sortedBy { it.store.ordinal }

    private companion object {
        const val STEAM_WEBSITE_CATEGORY = 13
        val STEAM_APP_URL_REGEX = Regex("""/app/(\d+)""")
    }
}

/** A store page of a game; [Store] declares the display order, Steam first. */
data class StoreLink(val store: Store, val url: String) {
    enum class Store(val label: String, val igdbCategories: Set<Int>) {
        STEAM("Steam", setOf(13)),
        EPIC("Epic Games Store", setOf(16)),
        GOG("GOG", setOf(17)),
        ITCH("itch.io", setOf(15)),
        APP_STORE("App Store", setOf(10, 11)),
        GOOGLE_PLAY("Google Play", setOf(12));

        companion object {
            fun forCategory(category: Int?): Store? = entries.firstOrNull { category in it.igdbCategories }
        }
    }

    companion object {
        fun steam(appId: Long) = StoreLink(Store.STEAM, "https://store.steampowered.com/app/$appId")
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
