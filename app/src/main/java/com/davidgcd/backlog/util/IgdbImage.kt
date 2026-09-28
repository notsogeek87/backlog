package com.davidgcd.backlog.util

/**
 * The one builder for IGDB image URLs. Mirrors the iOS app's IGDBImage
 * utility — never hand-build "images.igdb.com/…/t_size/…" at a call site.
 */
object IgdbImage {
    enum class Size(val apiValue: String) {
        CoverSmall("t_cover_small"),
        CoverBig("t_cover_big"),
        ScreenshotHuge("t_screenshot_huge"),
    }

    fun url(imageId: String, size: Size = Size.CoverBig): String =
        "https://images.igdb.com/igdb/image/upload/${size.apiValue}/$imageId.jpg"
}
