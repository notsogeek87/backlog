package com.davidgcd.backlog.data.local

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs Room's DAO in an in-memory SQLite database under Robolectric — no
 * emulator needed, so this executes in the same `testDebugUnitTest` task CI
 * already runs (see README's former "no DAO tests yet" gap).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class GameDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: GameDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.gameDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(igdbId: Long, name: String = "Game $igdbId", archived: Boolean = false, releaseDate: Long? = null) =
        GameEntity(igdbId = igdbId, name = name, isArchived = archived, firstReleaseDate = releaseDate)

    @Test
    fun `upsert then findById returns the same row`() = runTest {
        dao.upsert(entity(1))
        val found = dao.findById(1)
        assertEquals("Game 1", found?.name)
    }

    @Test
    fun `findById returns null for a missing id`() = runTest {
        assertNull(dao.findById(999))
    }

    @Test
    fun `upsert replaces an existing row instead of duplicating it`() = runTest {
        dao.upsert(entity(1, name = "Original"))
        dao.upsert(entity(1, name = "Renamed"))
        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Renamed", all.first().name)
    }

    @Test
    fun `activeGames excludes archived rows`() = runTest {
        dao.upsert(entity(1, archived = false))
        dao.upsert(entity(2, archived = true))
        val active = dao.activeGames()
        assertEquals(1, active.size)
        assertEquals(1L, active.first().igdbId)
    }

    @Test
    fun `activeGamesWithReleaseDate requires both non-archived and a date`() = runTest {
        dao.upsert(entity(1, archived = false, releaseDate = 100L))
        dao.upsert(entity(2, archived = false, releaseDate = null))
        dao.upsert(entity(3, archived = true, releaseDate = 100L))
        val result = dao.activeGamesWithReleaseDate()
        assertEquals(1, result.size)
        assertEquals(1L, result.first().igdbId)
    }

    @Test
    fun `delete removes the row`() = runTest {
        val game = entity(1)
        dao.upsert(game)
        dao.delete(game)
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun `activeCount only counts non-archived rows`() = runTest {
        dao.upsert(entity(1, archived = false))
        dao.upsert(entity(2, archived = false))
        dao.upsert(entity(3, archived = true))
        assertEquals(2, dao.activeCount())
    }
}
