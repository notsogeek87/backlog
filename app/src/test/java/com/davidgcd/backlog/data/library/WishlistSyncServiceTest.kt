package com.davidgcd.backlog.data.library

import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.GameSourceEntity
import com.davidgcd.backlog.data.local.gameStatus
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.IgdbService
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.ui.backlog.FakeGameDao
import com.davidgcd.backlog.ui.backlog.FakeIgdbApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WishlistSyncServiceTest {
    private class FakeWishlist(var ids: List<String> = emptyList(), var failure: LibraryException? = null) : WishlistSource {
        override suspend fun fetchWishlist(accountId: String): List<String> {
            failure?.let { throw it }
            return ids
        }
    }

    private val dao = FakeGameDao()
    private val sources = FakeGameSourceDao()
    private val accounts = FakeAccountStore()
    private val wishlist = FakeWishlist()
    private val catalog = FakeCatalog(
        external = mapOf("10" to 100L, "20" to 200L),
        games = listOf(Game(100, "Hades II"), Game(200, "Silksong")),
    )
    private val service = WishlistSyncService(
        source = wishlist,
        catalog = catalog,
        repository = BacklogRepository(dao, IgdbService(FakeIgdbApi()), Moshi.Builder().add(KotlinJsonAdapterFactory()).build()),
        sourceDao = sources,
        accounts = accounts,
        clock = { 5L },
    )

    private suspend fun connect() = accounts.connect(LibraryProviders.STEAM, "76561198000000000", "Tester")

    @Test
    fun `not connected fails without reading Steam`() = runTest {
        try {
            service.sync()
            fail("expected NOT_CONNECTED")
        } catch (e: LibraryException) {
            assertEquals(LibraryError.NOT_CONNECTED, e.error)
        }
    }

    @Test
    fun `adds wishlisted games with the wishlist status and tracks them`() = runTest {
        connect()
        wishlist.ids = listOf("10", "20", "999")

        val result = service.sync()

        assertEquals(3, result.total)
        assertEquals(2, result.added)
        assertEquals(1, result.unmatched)
        assertEquals(setOf(GameStatus.WISHLIST), dao.allGames().map { it.gameStatus }.toSet())
        assertEquals(setOf("10", "20"), sources.forProvider(LibraryProviders.STEAM_WISHLIST).map { it.externalId }.toSet())
    }

    @Test
    fun `a game already in the backlog keeps its status`() = runTest {
        connect()
        dao.upsert(GameEntity(100, "Hades II", status = GameStatus.PLAYED.name))
        wishlist.ids = listOf("10")

        val result = service.sync()

        assertEquals(0, result.added)
        assertEquals(GameStatus.PLAYED, dao.findById(100)!!.gameStatus)
    }

    @Test
    fun `running twice adds nothing more`() = runTest {
        connect()
        wishlist.ids = listOf("10")
        service.sync()

        val second = service.sync()

        assertEquals(0, second.added)
        assertEquals(1, dao.allGames().size)
    }

    @Test
    fun `a game that left the wishlist is archived, or promoted when owned`() = runTest {
        connect()
        wishlist.ids = listOf("10", "20")
        service.sync()
        sources.upsertAll(listOf(GameSourceEntity(LibraryProviders.STEAM, "20", 200L, lastSyncedAt = 1L)))

        wishlist.ids = listOf("30").also { catalog.external = catalog.external + ("30" to 300L); catalog.games += Game(300, "Hollow") }
        val result = service.sync()

        assertEquals(1, result.archived)
        assertEquals(1, result.promoted)
        assertTrue(dao.findById(100)!!.isArchived)
        assertEquals(GameStatus.BACKLOG, dao.findById(200)!!.gameStatus)
        assertFalse(dao.findById(200)!!.isArchived)
    }

    @Test
    fun `coming back to the wishlist restores the archived game`() = runTest {
        connect()
        wishlist.ids = listOf("10", "20")
        service.sync()
        wishlist.ids = listOf("20")
        service.sync()
        assertTrue(dao.findById(100)!!.isArchived)

        wishlist.ids = listOf("10", "20")
        val result = service.sync()

        assertEquals(1, result.restored)
        assertFalse(dao.findById(100)!!.isArchived)
    }

    @Test
    fun `a game the user moved on from is left alone when it leaves the wishlist`() = runTest {
        connect()
        wishlist.ids = listOf("10", "20")
        service.sync()
        dao.update(dao.findById(100)!!.copy(status = GameStatus.PLAYED.name))

        wishlist.ids = listOf("20")
        val result = service.sync()

        assertEquals(0, result.archived)
        assertFalse(dao.findById(100)!!.isArchived)
    }

    @Test
    fun `an empty answer never removes anything`() = runTest {
        connect()
        wishlist.ids = listOf("10")
        service.sync()

        wishlist.ids = emptyList()
        val result = service.sync()

        assertEquals(0, result.total)
        assertFalse(dao.findById(100)!!.isArchived)
    }
}
