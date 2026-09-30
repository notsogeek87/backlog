package com.davidgcd.backlog.ui.backlog

import com.davidgcd.backlog.model.GameStatus

/**
 * Mirrors the iOS app's SortOption: a stable name, never a translated or
 * positional value. The display label lives at the UI layer (see
 * `BacklogSort.label()` in BacklogScreen.kt) so it can be localized —
 * never stored or compared on.
 */
enum class BacklogSort {
    RECENTLY_ADDED,
    NAME,
    RELEASE_DATE,
    RATING,
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
    val status: GameStatus? = null,
) {
    val isActive: Boolean get() = showArchived || genre != null || platform != null || status != null
}
