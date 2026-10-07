package com.davidgcd.backlog.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games ORDER BY addedAt DESC")
    suspend fun allGames(): List<GameEntity>

    @Query("SELECT * FROM games WHERE igdbId = :igdbId LIMIT 1")
    suspend fun findById(igdbId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE igdbId = :igdbId LIMIT 1")
    fun observeById(igdbId: Long): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE isArchived = 0 AND firstReleaseDate IS NOT NULL")
    suspend fun activeGamesWithReleaseDate(): List<GameEntity>

    @Query("SELECT * FROM games WHERE isArchived = 0 ORDER BY addedAt ASC")
    suspend fun activeGames(): List<GameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: GameEntity)

    @Update
    suspend fun update(game: GameEntity)

    @Query("UPDATE games SET userRating = :rating WHERE igdbId = :igdbId")
    suspend fun setUserRating(igdbId: Long, rating: Int?)

    @Query("UPDATE games SET userRank = :rank WHERE igdbId = :igdbId")
    suspend fun setRank(igdbId: Long, rank: Int?)

    @Delete
    suspend fun delete(game: GameEntity)

    @Query("SELECT COUNT(*) FROM games WHERE isArchived = 0")
    suspend fun activeCount(): Int
}
