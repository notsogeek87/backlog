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

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE games ADD COLUMN status TEXT NOT NULL DEFAULT 'BACKLOG'")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE games ADD COLUMN userRank INTEGER")
        }
    }

    /** Films & séries, first shape (IMDb ids). Superseded by 6→7, kept as it shipped. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `movies` (" +
                    "`imdbId` TEXT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`year` INTEGER, `releaseDate` INTEGER, `posterUrl` TEXT, `genres` TEXT, `plot` TEXT, " +
                    "`imdbRating` REAL, `runtimeMinutes` INTEGER, `directors` TEXT, " +
                    "`isArchived` INTEGER NOT NULL, `status` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
                    "`userRank` INTEGER, `userRating` INTEGER, PRIMARY KEY(`imdbId`))",
            )
        }
    }

    /**
     * Films & séries move from IMDb ids (`tt…`) to TMDB keys (`movie:603` / `tv:1396`). The two id spaces
     * can't be mapped onto each other and the IMDb version was live for a few hours only, so the table is
     * rebuilt (SQLite of Android 8 can't rename a column).
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `movies`")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `movies` (" +
                    "`titleKey` TEXT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`year` INTEGER, `releaseDate` INTEGER, `posterUrl` TEXT, `genres` TEXT, `plot` TEXT, " +
                    "`tmdbRating` REAL, `runtimeMinutes` INTEGER, `directors` TEXT, " +
                    "`isArchived` INTEGER NOT NULL, `status` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
                    "`userRank` INTEGER, `userRating` INTEGER, PRIMARY KEY(`titleKey`))",
            )
        }
    }

    /** Films & séries: the top-billed actors are kept next to the director. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE movies ADD COLUMN `cast` TEXT")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
}
