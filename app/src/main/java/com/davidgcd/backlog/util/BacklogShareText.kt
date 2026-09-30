package com.davidgcd.backlog.util

import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.gameStatus
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.model.Ranking

/**
 * Plain-text rendering of the backlog for the system share sheet (messages, mail, notes…).
 * The only place that decides what a shared backlog looks like; labels come in from the caller
 * so this stays free of Android resources and unit-testable.
 *
 * Ranked games (see [Ranking]) lead as a numbered "my ranking" list, most loved first; the games
 * that aren't ranked yet follow, grouped by status.
 */
object BacklogShareText {
    data class Labels(
        val header: (count: Int) -> String,
        val status: (GameStatus) -> String,
        val ranking: String,
    )

    /** [links] maps an IGDB id to its igdb.com page; a game without one is listed by name only. */
    fun build(games: List<GameEntity>, labels: Labels, links: Map<Long, String> = emptyMap()): String {
        val active = games.filter { !it.isArchived }
        val ranked = Ranking.order(active).filter { it.userRank != null }
        val rankedIds = ranked.map { it.igdbId }.toSet()
        return buildString {
            append(labels.header(active.size))
            if (ranked.isNotEmpty()) {
                append("\n\n").append(labels.ranking)
                ranked.forEachIndexed { index, game ->
                    append("\n").append(index + 1).append(". ").append(game.name)
                    append(" (").append(labels.status(game.gameStatus)).append(")")
                    links[game.igdbId]?.let { append(" — ").append(it) }
                }
            }
            GameStatus.entries.forEach { status ->
                val inStatus = active
                    .filter { it.gameStatus == status && it.igdbId !in rankedIds }
                    .sortedBy { it.name.lowercase() }
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
