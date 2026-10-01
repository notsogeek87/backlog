package com.davidgcd.backlog.util

/**
 * Open Library serves each cover at three fixed sizes (`-S`, `-M`, `-L`): one place to pick the size,
 * like [TmdbImage] for films. A cover Open Library doesn't have is a 404 (`default=false`) instead of
 * a 1-pixel blank image, so the UI shows its own placeholder rather than an empty gap.
 */
object BookImage {
    private const val BASE = "https://covers.openlibrary.org/b/"
    private val SIZED = Regex("""^(https://covers\.openlibrary\.org/b/(?:id|olid|isbn)/[^/?]+)-[SML]\.jpg(\?.*)?$""")

    /** The stored cover of the book whose Open Library cover id is [coverId]. */
    fun byCoverId(coverId: Long): String = "${BASE}id/$coverId-M.jpg"

    /** Fallback when only the edition id is known; may 404 (then the placeholder shows). */
    fun byEditionId(editionId: String): String = "${BASE}olid/$editionId-M.jpg?default=false"

    /** A stored cover URL at [size] (`S`, `M` or `L`); a URL from another host is returned as is. */
    fun sized(url: String?, size: Char = 'M'): String? {
        if (url.isNullOrBlank()) return null
        val match = SIZED.find(url) ?: return url
        return "${match.groupValues[1]}-$size.jpg${match.groupValues[2]}"
    }
}
