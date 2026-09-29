package com.davidgcd.backlog.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameSourceDao {
    @Query("SELECT * FROM game_sources WHERE provider = :provider")
    suspend fun forProvider(provider: String): List<GameSourceEntity>

    @Query("SELECT * FROM game_sources WHERE igdbId = :igdbId")
    fun observeForGame(igdbId: Long): Flow<List<GameSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sources: List<GameSourceEntity>)

    @Query("DELETE FROM game_sources WHERE provider = :provider")
    suspend fun deleteForProvider(provider: String)
}
