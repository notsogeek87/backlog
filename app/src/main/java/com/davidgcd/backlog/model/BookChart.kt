package com.davidgcd.backlog.model

/**
 * The Open Library lists the Discover tab offers for books: what is trending this week, then the big
 * subjects. Stable names; labels live in strings.xml. A subject slug is Open Library's own (`/subjects/<slug>`).
 */
enum class BookChart(val path: String) {
    TRENDING("/trending/weekly.json"),
    SCIENCE_FICTION("/subjects/science_fiction.json"),
    FANTASY("/subjects/fantasy.json"),
    MYSTERY("/subjects/mystery_and_detective_stories.json"),
    ROMANCE("/subjects/romance.json"),
    CLASSICS("/subjects/classic_literature.json"),
}
