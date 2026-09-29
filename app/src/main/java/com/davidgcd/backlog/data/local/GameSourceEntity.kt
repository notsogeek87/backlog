package com.davidgcd.backlog.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * "This backlog game is also owned on <provider>" — one row per (provider, external id), so the
 * same game can carry Steam and (later) GOG sources without ever being duplicated in `games`.
 *
 * Deliberately not a foreign key to `games`: [GameDao.upsert] uses REPLACE (delete + insert), which
 * would cascade-delete the sources. A source whose game left the backlog is simply an orphan the
 * sync recognises as "removed earlier" (see LibrarySyncService).
 */
@Entity(
    tableName = "game_sources",
    primaryKeys = ["provider", "externalId"],
    indices = [Index("igdbId")],
)
data class GameSourceEntity(
    val provider: String,
    val externalId: String,
    val igdbId: Long,
    val playtimeMinutes: Int? = null,
    val recentPlaytimeMinutes: Int? = null,
    val lastSyncedAt: Long,
    /** The provider stopped returning this game. Informational only — never triggers a deletion. */
    val missingFromLibrary: Boolean = false,
)
