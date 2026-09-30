package com.davidgcd.backlog.model

/**
 * Where the player is at with a game. Persisted by [name] (see GameEntity.status) and written to
 * the CSV export the same way — a stable identifier, never the French label.
 */
enum class GameStatus {
    BACKLOG,
    PLAYED,
    COMPLETED,
    ;

    companion object {
        // PLAYED_UNFINISHED was this status's name before it became a plain PLAYED; rows and CSV
        // files written with it must keep reading back.
        private const val LEGACY_PLAYED_UNFINISHED = "PLAYED_UNFINISHED"

        fun fromName(name: String?): GameStatus {
            val key = name?.trim()
            if (key.equals(LEGACY_PLAYED_UNFINISHED, ignoreCase = true)) return PLAYED
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: BACKLOG
        }
    }
}
