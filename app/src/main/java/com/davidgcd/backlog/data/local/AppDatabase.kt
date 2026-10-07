package com.davidgcd.backlog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GameEntity::class, GameSourceEntity::class, MovieEntity::class, BookEntity::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun gameSourceDao(): GameSourceDao
    abstract fun movieDao(): MovieDao
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "backlog.db",
                )
                    .addMigrations(*Migrations.ALL)
                    .build().also { instance = it }
            }
    }
}
