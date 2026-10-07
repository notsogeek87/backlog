package com.davidgcd.backlog.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    suspend fun allBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE bookKey = :bookKey LIMIT 1")
    suspend fun findById(bookKey: String): BookEntity?

    @Query("SELECT * FROM books WHERE bookKey = :bookKey LIMIT 1")
    fun observeById(bookKey: String): Flow<BookEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET userRank = :rank WHERE bookKey = :bookKey")
    suspend fun setRank(bookKey: String, rank: Int?)

    @Delete
    suspend fun delete(book: BookEntity)
}
