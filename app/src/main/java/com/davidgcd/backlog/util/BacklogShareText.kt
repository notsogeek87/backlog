package com.davidgcd.backlog.util

import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.gameStatus
import com.davidgcd.backlog.model.GameStatus

/**
 * Plain-text rendering of the backlog for the system share sheet (messages, mail, notes…).
 * The only place that decides what a shared backlog looks like; labels come in from the caller
 * so this stays free of Android resources and unit-testable.
 */
object BacklogShareText {
    data class Labels(
        val header: (count: Int) -> String,
        val status: (GameStatus) -> String,
    )

    /** [links] maps an IGDB id to its igdb.com page; a game without one is listed by name only. */
    fun build(games: List<GameEntity>, labels: Labels, links: Map<Long, String> = emptyMap()): String {
        val active = games.filter { !it.isArchived }
        return buildString {
            append(labels.header(active.size))
            GameStatus.entries.forEach { status ->
                val inStatus = active.filter { it.gameStatus == status }.sortedBy { it.name.lowercase() }
                if (inStatus.isEmpty()) return@forEach
                append("\n\n").append(labels.status(status)).append(" (").append(inStatus.size).append(")")
                inStatus.forEach { game ->
                    append("\n• ").append(game.name)
                    links[game.igdbId]?.let { append(" — ").append(it) }
                }
            }
        }
    }
}
