package com.davidgcd.backlog.util

/**
 * TMDB serves each poster at fixed widths (`/t/p/w185/…`, `w500`, `original`). One place to pick
 * the size, like [IgdbImage] for games.
 */
object TmdbImage {
    private const val BASE = "https://image.tmdb.org/t/p/"
    private val SIZED = Regex("""^https://image\.tmdb\.org/t/p/(?:w\d+|original)(/.+)$""")

    /** A stored poster URL (or a bare `/abc.jpg` path) at [width] — one of TMDB's own: 92, 154, 185, 342, 500, 780. */
    fun poster(url: String?, width: Int = 500): String? {
        if (url.isNullOrBlank()) return null
        SIZED.find(url)?.let { return "${BASE}w$width${it.groupValues[1]}" }
        return if (url.startsWith("/")) "${BASE}w$width$url" else url
    }

    /** A provider logo (`logo_path`), small: they are shown at about 48 dp. */
    fun logo(logoPath: String?): String? = logoPath?.takeIf { it.startsWith("/") }?.let { "${BASE}w92$it" }

    /** A person's photo (`profile_path`), shown at about 72 dp. */
    fun profile(profilePath: String?): String? = profilePath?.takeIf { it.startsWith("/") }?.let { "${BASE}w185$it" }

    /** What gets stored: the reference-size URL of a `poster_path`. */
    fun stored(posterPath: String?): String? = posterPath?.takeIf { it.startsWith("/") }?.let { "${BASE}w500$it" }
}
