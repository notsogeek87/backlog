package com.davidgcd.backlog.model

/** The TMDB lists the Discover tab offers for films and series. Stable names; labels live in strings.xml. */
enum class MovieChart(val path: String, val kind: TitleKind) {
    POPULAR_MOVIES("/movie/popular", TitleKind.MOVIE),
    TOP_MOVIES("/movie/top_rated", TitleKind.MOVIE),
    POPULAR_SERIES("/tv/popular", TitleKind.SERIES),
    TOP_SERIES("/tv/top_rated", TitleKind.SERIES),
}
