package com.davidgcd.backlog.data.library

import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.IgdbService
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.ui.backlog.FakeGameDao
import com.davidgcd.backlog.ui.backlog.FakeIgdbApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LibrarySyncServiceTest {
    private val steam = LibraryProviders.STEAM
    private val dao = FakeGameDao()
    private val sources = FakeGameSourceDao()
    private val accounts = FakeAccountStore()
    private val provider = FakeLibraryProvider()
    private val catalog = FakeCatalog()
    private var now = 1_000L

    private val service = LibrarySyncService(
        providers = mapOf(steam to provider),
        catalog = catalog,
        repository = BacklogRepository(
            dao, IgdbService(FakeIgdbApi()),
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build(),
        ),
        sourceDao = sources,
        accounts = accounts,
        clock = { now },
    )

    private suspend fun connect() = accounts.connect(steam, "76561198000000000", "Tester")

    private fun lg(appId: Long, name: String, minutes: Int? = 60, recent: Int? = null) =
        LibraryGame(appId.toString(), name, minutes, recent)

    private suspend fun sync() = service.sync(steam)

    @Test
    fun `fetches a library and proposes a new game`() = runTest {
        connect()
        provider.library = listOf(lg(1091500, "Cyberpunk 2077", 5_160))
        catalog.external = mapOf("1091500" to 100L)
        catalog.games = listOf(Game(100, "Cyberpunk 2077"))

        val preview = sync()

        assertEquals(1, preview.ownedCount)
        val item = preview.items.single()
        assertEquals(ItemStatus.NEW, item.status)
        assertEquals(100L, item.igdbId)
        assertTrue("sync alone must not add anything", dao.allGames().isEmpty())
    }

    @Test
    fun `importing a new game adds it once with its Steam source`() = runTest {
        connect()
        provider.library = listOf(lg(1091500, "Cyberpunk 2077", 5_160, recent = 30))
        catalog.external = mapOf("1091500" to 100L)
        catalog.games = listOf(Game(100, "Cyberpunk 2077"))

        val preview = sync()
        val result = service.import(steam, preview, setOf("1091500"))

        assertEquals(1, result.added)
        assertEquals(listOf("Cyberpunk 2077"), dao.allGames().map { it.name })
        assertEquals(1091500L, dao.findById(100)?.steamAppId)
        val source = sources.forProvider(steam).single()
        assertEquals(100L, source.igdbId)
        assertEquals(5_160, source.playtimeMinutes)
        assertEquals(30, source.recentPlaytimeMinutes)
    }

    @Test
    fun `a game already in the backlog is detected and linked without a duplicate`() = runTest {
        connect()
        dao.upsert(GameEntity(igdbId = 100, name = "Cyberpunk 2077"))
        provider.library = listOf(lg(1091500, "Cyberpunk 2077", 5_160))
        catalog.external = mapOf("1091500" to 100L)

        val preview = sync()

        assertEquals(ItemStatus.EXISTING, preview.items.single().status)
        assertEquals(0, preview.newCount)
        assertEquals(1, dao.allGames().size)
        assertEquals(5_160, sources.forProvider(steam).single().playtimeMinutes)
    }

    @Test
    fun `existing status, priority-like fields and dates are never overwritten`() = runTest {
        connect()
        val original = GameEntity(igdbId = 100, name = "Cyberpunk 2077", isArchived = true, addedAt = 42, totalRating = 91.0)
        dao.upsert(original)
        provider.library = listOf(lg(1091500, "Cyberpunk 2077", 5_160))
        catalog.external = mapOf("1091500" to 100L)

        val preview = sync()
        service.import(steam, preview, preview.items.map { it.key }.toSet())

        // Only the steamAppId backfill may differ, exactly like the game-detail screen already does.
        assertEquals(original.copy(steamAppId = null), dao.findById(100)?.copy(steamAppId = null))
    }

    @Test
    fun `syncing and importing twice never duplicates games or sources`() = runTest {
        connect()
        provider.library = listOf(lg(10, "Hades", 2_520))
        catalog.external = mapOf("10" to 200L)
        catalog.games = listOf(Game(200, "Hades"))

        val first = sync()
        service.import(steam, first, setOf("10"))
        val second = sync()
        val result = service.import(steam, second, setOf("10")) // stale selection: nothing importable anymore

        assertEquals(ItemStatus.LINKED, second.items.single().status)
        assertEquals(0, result.added)
        assertEquals(1, dao.allGames().size)
        assertEquals(1, sources.forProvider(steam).size)
    }

    @Test
    fun `resync refreshes playtime and picks up newly bought games`() = runTest {
        connect()
        provider.library = listOf(lg(10, "Hades", 60))
        catalog.external = mapOf("10" to 200L, "20" to 300L)
        catalog.games = listOf(Game(200, "Hades"), Game(300, "Stardew Valley"))
        service.import(steam, sync(), setOf("10"))

        now = 2_000L
        provider.library = listOf(lg(10, "Hades", 180), lg(20, "Stardew Valley", null))
        val preview = sync()

        assertEquals(180, sources.forProvider(steam).single { it.externalId == "10" }.playtimeMinutes)
        assertEquals(2_000L, sources.forProvider(steam).single { it.externalId == "10" }.lastSyncedAt)
        assertEquals(listOf("20"), preview.items(ItemStatus.NEW).map { it.key })
        assertEquals(1, preview.newCount)
    }

    @Test
    fun `a game Steam stops returning is kept and only flagged`() = runTest {
        connect()
        provider.library = listOf(lg(10, "Hades"), lg(20, "Stardew Valley"))
        catalog.external = mapOf("10" to 200L, "20" to 300L)
        catalog.games = listOf(Game(200, "Hades"), Game(300, "Stardew Valley"))
        service.import(steam, sync(), setOf("10", "20"))

        provider.library = listOf(lg(10, "Hades"))
        val preview = sync()

        assertEquals(2, dao.allGames().size)
        assertEquals(1, preview.missingFromLibraryCount)
        assertTrue(sources.forProvider(steam).single { it.externalId == "20" }.missingFromLibrary)
        assertFalse(sources.forProvider(steam).single { it.externalId == "10" }.missingFromLibrary)
    }

    @Test
    fun `an empty answer does not flag the previous library as missing`() = runTest {
        connect()
        provider.library = listOf(lg(10, "Hades"))
        catalog.external = mapOf("10" to 200L)
        catalog.games = listOf(Game(200, "Hades"))
        service.import(steam, sync(), setOf("10"))

        provider.library = emptyList()
        val preview = sync()

        assertTrue(preview.items.isEmpty())
        assertFalse(sources.forProvider(steam).single().missingFromLibrary)
    }

    @Test
    fun `an account with no games gives an empty preview`() = runTest {
        connect()
        provider.library = emptyList()
        val preview = sync()
        assertEquals(0, preview.ownedCount)
        assertEquals(0, accounts.get(steam)?.ownedCount)
    }

    @Test
    fun `a private library surfaces as PRIVATE_LIBRARY and writes nothing`() = runTest {
        connect()
        provider.failure = LibraryException(LibraryError.PRIVATE_LIBRARY)
        try {
            sync()
            fail("expected a LibraryException")
        } catch (e: LibraryException) {
            assertEquals(LibraryError.PRIVATE_LIBRARY, e.error)
        }
        assertTrue(sources.rows.value.isEmpty())
    }

    @Test
    fun `a Steam API error is propagated without touching data`() = runTest {
        connect()
        dao.upsert(GameEntity(igdbId = 1, name = "Keep me"))
        provider.failure = LibraryException(LibraryError.UNAVAILABLE)
        try {
            sync()
            fail("expected a LibraryException")
        } catch (e: LibraryException) {
            assertEquals(LibraryError.UNAVAILABLE, e.error)
        }
        assertEquals(1, dao.allGames().size)
        assertTrue(sources.rows.value.isEmpty())
    }

    @Test
    fun `syncing while disconnected reports NOT_CONNECTED`() = runTest {
        try {
            sync()
            fail("expected a LibraryException")
        } catch (e: LibraryException) {
            assertEquals(LibraryError.NOT_CONNECTED, e.error)
        }
    }

    @Test
    fun `a game with zero or unknown playtime is still importable`() = runTest {
        connect()
        provider.library = listOf(lg(1, "Never Played", 0), lg(2, "No Figure", null))
        catalog.external = mapOf("1" to 11L, "2" to 12L)
        catalog.games = listOf(Game(11, "Never Played"), Game(12, "No Figure"))

        val result = service.import(steam, sync(), setOf("1", "2"))

        assertEquals(2, result.added)
        assertEquals(0, sources.forProvider(steam).single { it.externalId == "1" }.playtimeMinutes)
        assertNull(sources.forProvider(steam).single { it.externalId == "2" }.playtimeMinutes)
    }

    @Test
    fun `a game linked through the legacy steamAppId column is recognised`() = runTest {
        connect()
        dao.upsert(GameEntity(igdbId = 100, name = "Whatever IGDB calls it", steamAppId = 620))
        provider.library = listOf(lg(620, "Portal 2"))

        val preview = sync()

        assertEquals(ItemStatus.EXISTING, preview.items.single().status)
        assertEquals(1, dao.allGames().size)
    }

    @Test
    fun `an identical title in the backlog is linked, a subtitle variant is only proposed`() = runTest {
        connect()
        dao.upsert(GameEntity(igdbId = 1, name = "Stardew Valley"))
        dao.upsert(GameEntity(igdbId = 2, name = "The Witcher 3: Wild Hunt"))
        provider.library = listOf(lg(413150, "Stardew Valley"), lg(292030, "The Witcher 3"))

        val preview = sync()

        assertEquals(ItemStatus.EXISTING, preview.items.single { it.key == "413150" }.status)
        val witcher = preview.items.single { it.key == "292030" }
        assertEquals(ItemStatus.UNCERTAIN, witcher.status)
        assertEquals(2L, witcher.igdbId)
        assertTrue(witcher.candidateInBacklog)
        assertEquals("nothing linked for the uncertain one yet", 1, sources.forProvider(steam).size)
    }

    @Test
    fun `DOOM and DOOM Eternal are different games`() = runTest {
        connect()
        dao.upsert(GameEntity(igdbId = 1, name = "DOOM"))
        provider.library = listOf(lg(782330, "DOOM Eternal"))
        catalog.external = mapOf("782330" to 500L)
        catalog.games = listOf(Game(500, "DOOM Eternal"))

        val item = sync().items.single()

        assertEquals(ItemStatus.NEW, item.status)
        assertEquals(500L, item.igdbId)
    }

    @Test
    fun `accepting an uncertain match links it without creating a game`() = runTest {
        connect()
        dao.upsert(GameEntity(igdbId = 2, name = "The Witcher 3: Wild Hunt"))
        provider.library = listOf(lg(292030, "The Witcher 3", 7_200))

        val preview = sync()
        val result = service.import(steam, preview, setOf("292030"))

        assertEquals(0, result.added)
        assertEquals(1, dao.allGames().size)
        assertEquals(2L, sources.forProvider(steam).single().igdbId)
    }

    @Test
    fun `a game removed from the backlog is offered again but not preselected data-wise`() = runTest {
        connect()
        provider.library = listOf(lg(10, "Hades"))
        catalog.external = mapOf("10" to 200L)
        catalog.games = listOf(Game(200, "Hades"))
        service.import(steam, sync(), setOf("10"))
        dao.delete(dao.findById(200)!!)

        val item = sync().items.single()

        assertEquals(ItemStatus.NEW, item.status)
        assertTrue(item.previouslyRemoved)
    }

    @Test
    fun `two Steam apps mapping to one IGDB game create a single game`() = runTest {
        connect()
        provider.library = listOf(lg(1, "Game"), lg(2, "Game Demo"))
        catalog.external = mapOf("1" to 9L, "2" to 9L)
        catalog.games = listOf(Game(9, "Game"))

        val result = service.import(steam, sync(), setOf("1", "2"))

        assertEquals(1, result.added)
        assertEquals(1, dao.allGames().size)
        assertNotNull(sources.forProvider(steam).firstOrNull { it.externalId == "2" })
    }

    @Test
    fun `stats after an import reflect what was added`() = runTest {
        connect()
        provider.library = listOf(lg(1, "A"), lg(2, "B"))
        catalog.external = mapOf("1" to 1L, "2" to 2L)
        catalog.games = listOf(Game(1, "A"), Game(2, "B"))

        val preview = sync()
        assertEquals(2, accounts.get(steam)?.newCount)
        service.import(steam, preview, setOf("1"))

        val account = accounts.get(steam)!!
        assertEquals(2, account.ownedCount)
        assertEquals(1, account.alreadyInBacklogCount)
        assertEquals(1, account.newCount)
    }
}
