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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `game_sources` (" +
                    "`provider` TEXT NOT NULL, `externalId` TEXT NOT NULL, `igdbId` INTEGER NOT NULL, " +
                    "`playtimeMinutes` INTEGER, `recentPlaytimeMinutes` INTEGER, " +
                    "`lastSyncedAt` INTEGER NOT NULL, `missingFromLibrary` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`provider`, `externalId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_game_sources_igdbId` ON `game_sources` (`igdbId`)")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
