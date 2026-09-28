package com.davidgcd.backlog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GameEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "backlog.db",
                )
                    // No installed base yet at this pre-release stage — a real migration
                    // replaces this the moment the app ships to a first user (see README).
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
