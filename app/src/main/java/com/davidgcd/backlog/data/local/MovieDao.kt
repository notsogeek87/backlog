package com.davidgcd.backlog.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies ORDER BY addedAt DESC")
    suspend fun allMovies(): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE titleKey = :titleKey LIMIT 1")
    suspend fun findById(titleKey: String): MovieEntity?

    @Query("SELECT * FROM movies WHERE titleKey = :titleKey LIMIT 1")
    fun observeById(titleKey: String): Flow<MovieEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(movie: MovieEntity)

    @Update
    suspend fun update(movie: MovieEntity)

    @Query("UPDATE movies SET userRank = :rank WHERE titleKey = :titleKey")
    suspend fun setRank(titleKey: String, rank: Int?)

    @Delete
    suspend fun delete(movie: MovieEntity)
}
