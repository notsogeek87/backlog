package com.davidgcd.backlog.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.davidgcd.backlog.model.GameStatus

/**
 * The persisted row (Room's equivalent of the SwiftData @Model on the iOS app).
 * Collections that Room can't store natively (genres, platforms) are cached
 * as JSON strings, the same "JSON caching" pattern the iOS app uses for
 * fields CloudKit can't store natively.
 */
@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val igdbId: Long,
    val name: String,
    val coverImageId: String? = null,
    val firstReleaseDate: Long? = null,
    val genresJson: String? = null,
    val platformsJson: String? = null,
    val summary: String? = null,
    val totalRating: Double? = null,
    val steamAppId: Long? = null,
    val isArchived: Boolean = false,
    /** [com.davidgcd.backlog.model.GameStatus] name; stored as text so a new status never needs a type converter. */
    val status: String = GameStatus.BACKLOG.name,
    val addedAt: Long = System.currentTimeMillis(),
)

val GameEntity.gameStatus: GameStatus get() = GameStatus.fromName(status)
