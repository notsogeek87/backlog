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

    @Query("SELECT * FROM movies WHERE imdbId = :imdbId LIMIT 1")
    suspend fun findById(imdbId: String): MovieEntity?

    @Query("SELECT * FROM movies WHERE imdbId = :imdbId LIMIT 1")
    fun observeById(imdbId: String): Flow<MovieEntity?>

    @Query("SELECT * FROM movies WHERE posterUrl IS NULL LIMIT :limit")
    suspend fun withoutPoster(limit: Int): List<MovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(movie: MovieEntity)

    @Update
    suspend fun update(movie: MovieEntity)

    @Query("UPDATE movies SET userRank = :rank WHERE imdbId = :imdbId")
    suspend fun setRank(imdbId: String, rank: Int?)

    @Query("UPDATE movies SET posterUrl = :posterUrl WHERE imdbId = :imdbId")
    suspend fun setPoster(imdbId: String, posterUrl: String)

    @Delete
    suspend fun delete(movie: MovieEntity)
}
