package com.davidgcd.backlog.data.library.steam

import com.davidgcd.backlog.data.library.LibraryError
import com.davidgcd.backlog.data.library.LibraryException
import com.davidgcd.backlog.data.remote.OwnedGame
import com.davidgcd.backlog.data.remote.OwnedGamesEnvelope
import com.davidgcd.backlog.data.remote.OwnedGamesResponse
import com.davidgcd.backlog.data.remote.PlayerSummariesEnvelope
import com.davidgcd.backlog.data.remote.PlayersResponse
import com.davidgcd.backlog.data.remote.SteamPlayer
import com.davidgcd.backlog.data.remote.SteamWebApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class SteamLibraryProviderTest {
    private class FakeSteamWebApi(
        var owned: () -> OwnedGamesEnvelope = { OwnedGamesEnvelope() },
        var players: () -> PlayerSummariesEnvelope = { PlayerSummariesEnvelope() },
        var wishlist: () -> com.davidgcd.backlog.data.remote.WishlistEnvelope = { com.davidgcd.backlog.data.remote.WishlistEnvelope() },
    ) : SteamWebApi {
        override suspend fun ownedGames(steamId: String, includeAppInfo: Int, includePlayedFreeGames: Int, format: String) = owned()
        override suspend fun wishlist(steamId: String, format: String) = wishlist()
        override suspend fun playerSummaries(steamIds: String, format: String) = players()
    }

    private val api = FakeSteamWebApi()
    private val provider = SteamLibraryProvider(api)

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    private suspend fun errorOf(block: suspend () -> Any?): LibraryError = try {
        block()
        fail("expected a LibraryException")
        error("unreachable")
    } catch (e: LibraryException) {
        e.error
    }

    @Test
    fun `maps owned games with playtime and header image`() = runTest {
        api.owned = {
            OwnedGamesEnvelope(OwnedGamesResponse(2, listOf(
                OwnedGame(1091500, "Cyberpunk 2077", playtimeForever = 5_160, playtime2Weeks = 120),
                OwnedGame(1145360, " Hades ", playtimeForever = 0),
            )))
        }

        val games = provider.fetchLibrary("76561198000000000")

        assertEquals(2, games.size)
        assertEquals("1091500", games[0].externalId)
        assertEquals(5_160, games[0].playtimeMinutes)
        assertEquals(120, games[0].recentPlaytimeMinutes)
        assertTrue(games[0].imageUrl!!.endsWith("/apps/1091500/header.jpg"))
        assertEquals("Hades", games[1].name)
        assertEquals(0, games[1].playtimeMinutes)
    }

    @Test
    fun `a game without playtime fields is still returned`() = runTest {
        api.owned = { OwnedGamesEnvelope(OwnedGamesResponse(1, listOf(OwnedGame(10, "Counter-Strike")))) }
        val game = provider.fetchLibrary("1").single()
        assertNull(game.playtimeMinutes)
        assertNull(game.recentPlaytimeMinutes)
    }

    @Test
    fun `entries without a name are dropped`() = runTest {
        api.owned = { OwnedGamesEnvelope(OwnedGamesResponse(2, listOf(OwnedGame(1, null), OwnedGame(2, "Named")))) }
        assertEquals(listOf("2"), provider.fetchLibrary("1").map { it.externalId })
    }

    @Test
    fun `a public account with zero games is an empty library, not an error`() = runTest {
        api.owned = { OwnedGamesEnvelope(OwnedGamesResponse(gameCount = 0)) }
        assertTrue(provider.fetchLibrary("1").isEmpty())
    }

    @Test
    fun `Steam's empty envelope means a private library`() = runTest {
        api.owned = { OwnedGamesEnvelope(OwnedGamesResponse()) }
        assertEquals(LibraryError.PRIVATE_LIBRARY, errorOf { provider.fetchLibrary("1") })
    }

    @Test
    fun `HTTP failures are classified`() = runTest {
        api.owned = { throw http(500) }
        assertEquals(LibraryError.UNAVAILABLE, errorOf { provider.fetchLibrary("1") })
        api.owned = { throw http(403) }
        assertEquals(LibraryError.UNAVAILABLE, errorOf { provider.fetchLibrary("1") })
        api.owned = { throw http(429) }
        assertEquals(LibraryError.RATE_LIMITED, errorOf { provider.fetchLibrary("1") })
    }

    @Test
    fun `network errors and timeouts are unavailable, unexpected ones unknown`() = runTest {
        api.owned = { throw SocketTimeoutException("timeout") }
        assertEquals(LibraryError.UNAVAILABLE, errorOf { provider.fetchLibrary("1") })
        api.owned = { throw IOException("offline") }
        assertEquals(LibraryError.UNAVAILABLE, errorOf { provider.fetchLibrary("1") })
        api.owned = { throw IllegalStateException("bad json") }
        assertEquals(LibraryError.UNKNOWN, errorOf { provider.fetchLibrary("1") })
    }

    @Test
    fun `without an API key the library is unavailable and no call is made`() = runTest {
        var called = false
        api.owned = { called = true; OwnedGamesEnvelope() }
        assertEquals(LibraryError.UNAVAILABLE, errorOf { SteamLibraryProvider(api, isConfigured = false).fetchLibrary("1") })
        assertTrue(!called)
    }

    @Test
    fun `profile lookup returns the persona name, or ACCOUNT_NOT_FOUND`() = runTest {
        api.players = { PlayerSummariesEnvelope(PlayersResponse(listOf(SteamPlayer("1", "Tester", 3)))) }
        assertEquals("Tester", provider.fetchProfile("1").displayName)

        api.players = { PlayerSummariesEnvelope(PlayersResponse(emptyList())) }
        assertEquals(LibraryError.ACCOUNT_NOT_FOUND, errorOf { provider.fetchProfile("1") })
    }

    @Test
    fun `wishlist returns distinct app ids, empty for a private profile`() = runTest {
        api.wishlist = { com.davidgcd.backlog.data.remote.WishlistEnvelope(com.davidgcd.backlog.data.remote.WishlistResponse(listOf(
            com.davidgcd.backlog.data.remote.WishlistItem(10), com.davidgcd.backlog.data.remote.WishlistItem(20), com.davidgcd.backlog.data.remote.WishlistItem(10),
        ))) }
        assertEquals(listOf("10", "20"), provider.fetchWishlist("76561198000000000"))

        api.wishlist = { com.davidgcd.backlog.data.remote.WishlistEnvelope(com.davidgcd.backlog.data.remote.WishlistResponse()) }
        assertTrue(provider.fetchWishlist("76561198000000000").isEmpty())
    }
}
