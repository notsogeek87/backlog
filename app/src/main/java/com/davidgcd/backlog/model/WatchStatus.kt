package com.davidgcd.backlog.model

/**
 * Where the viewer is at with a film or a series. Persisted by [name] (see MovieEntity.status),
 * never by its French label.
 */
enum class WatchStatus {
    TO_WATCH,
    WATCHING,
    WATCHED,
    ;

    companion object {
        fun fromName(name: String?): WatchStatus {
            val key = name?.trim()
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: TO_WATCH
        }
    }
}

/** Films and series share one list, told apart by this. Persisted by [name]. */
enum class TitleKind {
    MOVIE,
    SERIES,
    ;

    companion object {
        fun fromName(name: String?): TitleKind =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: MOVIE
    }
}
