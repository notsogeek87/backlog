package com.davidgcd.backlog.util

/**
 * IMDb posters are served at full resolution unless the URL asks for a size (`._V1_UY<height>_.jpg`,
 * the same trick imdb.com itself uses). One place for it, like [IgdbImage] for games.
 */
object ImdbImage {
    private val SIZED = Regex("""^(.*?)\._V1_.*(\.\w+)$""")

    fun poster(url: String?, height: Int = 400): String? {
        if (url.isNullOrBlank()) return null
        val match = SIZED.find(url) ?: return url
        return "${match.groupValues[1]}._V1_UY${height}_${match.groupValues[2]}"
    }
}
