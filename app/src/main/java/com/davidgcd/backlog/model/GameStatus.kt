package com.davidgcd.backlog.model

/**
 * Where the player is at with a game. Persisted by [name] (see GameEntity.status) and written to
 * the CSV export the same way — a stable identifier, never the French label.
 */
enum class GameStatus {
    BACKLOG,
    PLAYED_UNFINISHED,
    COMPLETED,
    ;

    companion object {
        fun fromName(name: String?): GameStatus =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: BACKLOG
    }
}
