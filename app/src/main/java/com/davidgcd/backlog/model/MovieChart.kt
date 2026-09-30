package com.davidgcd.backlog.model

/** The IMDb charts the Discover tab offers for films and series. Stable names; labels live in strings.xml. */
enum class MovieChart(val path: String) {
    POPULAR_MOVIES("/chart/moviemeter/"),
    TOP_MOVIES("/chart/top/"),
    POPULAR_SERIES("/chart/tvmeter/"),
    TOP_SERIES("/chart/toptv/"),
}
