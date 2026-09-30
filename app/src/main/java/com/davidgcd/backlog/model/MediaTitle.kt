package com.davidgcd.backlog.model

/**
 * A film or series as TMDB describes it — the movie-side counterpart of [Game]: a transient
 * response shape, never persisted as is (see data/local/MovieEntity).
 */
data class MediaTitle(
    /** TMDB key, `movie:603` or `tv:1396` (see [TitleKey]): TMDB numbers films and series separately. */
    val id: String,
    val title: String,
    val kind: TitleKind = TitleKind.MOVIE,
    val year: Int? = null,
    /** UTC epoch seconds, like [Game.firstReleaseDate]; null when TMDB only gave a year. */
    val releaseDate: Long? = null,
    /** Full image URL (see util/TmdbImage for sizes). */
    val posterUrl: String? = null,
    val genres: List<String> = emptyList(),
    val plot: String? = null,
    /** TMDB average vote, 0–10. */
    val rating: Double? = null,
    val runtimeMinutes: Int? = null,
    val directors: String? = null,
    val cast: String? = null,
) {
    val tmdbUrl: String get() = TitleKey.url(id)
}

/** The app-wide id of a film/series: `<movie|tv>:<tmdb id>`. */
object TitleKey {
    fun of(kind: TitleKind, tmdbId: Long) = "${prefix(kind)}:$tmdbId"

    fun kind(key: String): TitleKind? = when (key.substringBefore(':')) {
        "movie" -> TitleKind.MOVIE
        "tv" -> TitleKind.SERIES
        else -> null
    }

    fun tmdbId(key: String): Long? = key.substringAfter(':', "").toLongOrNull()

    fun prefix(kind: TitleKind) = if (kind == TitleKind.SERIES) "tv" else "movie"

    fun url(key: String) = "https://www.themoviedb.org/${kind(key)?.let(::prefix) ?: "movie"}/${tmdbId(key) ?: ""}"
}
