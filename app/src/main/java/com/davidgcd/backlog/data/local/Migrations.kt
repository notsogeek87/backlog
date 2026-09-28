package com.davidgcd.backlog.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Every schema change gets a real migration here — never fallbackToDestructiveMigration again. */
object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE games ADD COLUMN steamAppId INTEGER")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
