package com.davidgcd.backlog.data.local

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class GameSourceDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: GameSourceDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = database.gameSourceDao()
    }

    @After
    fun tearDown() = database.close()

    private fun source(provider: String, ext: String, igdbId: Long, minutes: Int? = 10) =
        GameSourceEntity(provider, ext, igdbId, playtimeMinutes = minutes, lastSyncedAt = 1)

    @Test
    fun `one game can carry several providers`() = runTest {
        dao.upsertAll(listOf(source("steam", "1", 7), source("gog", "abc", 7)))
        assertEquals(setOf("steam", "gog"), dao.observeForGameOnce(7))
    }

    @Test
    fun `upserting the same provider and external id updates instead of duplicating`() = runTest {
        dao.upsertAll(listOf(source("steam", "1", 7, minutes = 10)))
        dao.upsertAll(listOf(source("steam", "1", 7, minutes = 99)))
        assertEquals(listOf(99), dao.forProvider("steam").map { it.playtimeMinutes })
    }

    @Test
    fun `deleting a provider leaves the other one alone`() = runTest {
        dao.upsertAll(listOf(source("steam", "1", 7), source("gog", "abc", 7)))
        dao.deleteForProvider("steam")
        assertEquals(emptyList<GameSourceEntity>(), dao.forProvider("steam"))
        assertEquals(1, dao.forProvider("gog").size)
    }

    @Test
    fun `adding a source never touches the games table`() = runTest {
        database.gameDao().upsert(GameEntity(igdbId = 7, name = "Game", isArchived = true, addedAt = 5))
        dao.upsertAll(listOf(source("steam", "1", 7)))
        assertEquals(GameEntity(igdbId = 7, name = "Game", isArchived = true, addedAt = 5), database.gameDao().findById(7))
    }

    private suspend fun GameSourceDao.observeForGameOnce(igdbId: Long) =
        observeForGame(igdbId).first().map { it.provider }.toSet()
}
