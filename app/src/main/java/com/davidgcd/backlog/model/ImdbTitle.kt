package com.davidgcd.backlog.model

/**
 * A film or series as IMDb describes it — the movie-side counterpart of [Game]: a transient
 * response shape, never persisted as is (see data/local/MovieEntity).
 */
data class ImdbTitle(
    /** IMDb "const", e.g. `tt0111161`. */
    val id: String,
    val title: String,
    val kind: TitleKind = TitleKind.MOVIE,
    val year: Int? = null,
    /** UTC epoch seconds, like [Game.firstReleaseDate]; null when IMDb only gave a year. */
    val releaseDate: Long? = null,
    val posterUrl: String? = null,
    val genres: List<String> = emptyList(),
    val plot: String? = null,
    /** IMDb user rating, 0–10. */
    val rating: Double? = null,
    val runtimeMinutes: Int? = null,
    val directors: String? = null,
    val cast: String? = null,
) {
    val imdbUrl: String get() = imdbTitleUrl(id)

    companion object {
        fun imdbTitleUrl(id: String) = "https://www.imdb.com/title/$id/"
    }
}
