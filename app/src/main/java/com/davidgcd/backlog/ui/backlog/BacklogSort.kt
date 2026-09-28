package com.davidgcd.backlog.ui.backlog

/** Mirrors the iOS app's SortOption: a stable name, never a translated/positional value. */
enum class BacklogSort(val label: String) {
    RECENTLY_ADDED("Recently added"),
    NAME("Name (A-Z)"),
    RELEASE_DATE("Release date"),
    RATING("Rating"),
}

/**
 * `genre`/`platform` are `null` for "any". `showArchived` mirrors the iOS
 * Backlog filter's archived toggle — off by default, so an all-archived
 * library still shows the empty state rather than a full one.
 */
data class BacklogFilter(
    val showArchived: Boolean = false,
    val genre: String? = null,
    val platform: String? = null,
) {
    val isActive: Boolean get() = showArchived || genre != null || platform != null
}
