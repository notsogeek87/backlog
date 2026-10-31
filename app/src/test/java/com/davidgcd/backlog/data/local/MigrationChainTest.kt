package com.davidgcd.backlog.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Plays the WHOLE migration chain on a real file database: a version-1 database (the first schema shipped:
 * only the `games` table) holding a game is opened by the current [AppDatabase] with [Migrations.ALL].
 * Room then checks every table against the current entities and throws if a migration left the schema
 * different from what the code expects — the failure that would otherwise only show on a user's phone, at
 * the update. The game must also come out intact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class MigrationChainTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-chain-test.db"

    @Before
    fun createVersionOne() {
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            // Schema 1: GameEntity before steamAppId (1→2), status (3→4) and userRank (4→5).
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `games` (`igdbId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "`coverImageId` TEXT, `firstReleaseDate` INTEGER, `genresJson` TEXT, `platformsJson` TEXT, " +
                    "`summary` TEXT, `totalRating` REAL, `isArchived` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`igdbId`))",
            )
            db.execSQL(
                "INSERT INTO games (igdbId, name, coverImageId, totalRating, isArchived, addedAt) " +
                    "VALUES (1942, 'The Witcher 3', 'co1wyy', 92.5, 0, 1700000000000)",
            )
            db.version = 1
        }
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun `a version 1 database migrates to the current schema and keeps its games`() {
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            // Opening triggers the migrations and Room's schema validation (throws on any mismatch).
            val db = database.openHelper.writableDatabase
            assertEquals(11, db.version)
            db.query("SELECT name, status, userRank, steamAppId, coverImageId, ratedAt FROM games WHERE igdbId = 1942").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals("The Witcher 3", c.getString(0))
                assertEquals("BACKLOG", c.getString(1))
                assertNull(c.getString(2))
                assertNull(c.getString(3))
                assertEquals("co1wyy", c.getString(4))
                assertNull(c.getString(5))
            }
            // The tables added along the way exist and are usable.
            for (table in listOf("game_sources", "movies", "books")) {
                db.query("SELECT count(*) FROM $table").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            }
        } finally {
            database.close()
        }
    }
}
