package com.davidgcd.backlog.data.library.steam

import com.davidgcd.backlog.data.library.GameLibraryProvider
import com.davidgcd.backlog.data.library.LibraryError
import com.davidgcd.backlog.data.library.LibraryException
import com.davidgcd.backlog.data.library.LibraryGame
import com.davidgcd.backlog.data.library.LibraryProviders
import com.davidgcd.backlog.data.library.toLibraryException
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.remote.SteamWebApi
import com.davidgcd.backlog.util.AppLogger
import kotlinx.coroutines.CancellationException

data class SteamProfile(val steamId: String, val displayName: String?)

/**
 * Steam library through the official Web API (`IPlayerService/GetOwnedGames`). The API key is added
 * by an OkHttp interceptor; [isConfigured] only says whether the build has one (or a proxy) to use.
 */
class SteamLibraryProvider(
    private val api: SteamWebApi,
    private val isConfigured: Boolean = true,
) : GameLibraryProvider {
    override val id = LibraryProviders.STEAM

    /** IGDB `external_game_sources`: 1 = Steam. */
    override val igdbExternalSourceId = 1

    override fun existingLinkId(game: GameEntity): String? = game.steamAppId?.toString()

    override suspend fun fetchLibrary(accountId: String): List<LibraryGame> {
        ensureConfigured()
        val response = guarded { api.ownedGames(accountId) }.response
        // No `game_count` at all = Steam's empty envelope for private game details (an empty
        // public library still says `game_count: 0`).
        if (response?.gameCount == null) {
            throw LibraryException(LibraryError.PRIVATE_LIBRARY, "GetOwnedGames returned an empty envelope for $accountId")
        }
        return response.games.orEmpty()
            .filter { !it.name.isNullOrBlank() }
            .map {
                LibraryGame(
                    externalId = it.appid.toString(),
                    name = it.name!!.trim(),
                    playtimeMinutes = it.playtimeForever,
                    recentPlaytimeMinutes = it.playtime2Weeks,
                    imageUrl = headerImageUrl(it.appid),
                )
            }
    }

    suspend fun fetchProfile(steamId: String): SteamProfile {
        ensureConfigured()
        val player = guarded { api.playerSummaries(steamId) }.response?.players?.firstOrNull()
            ?: throw LibraryException(LibraryError.ACCOUNT_NOT_FOUND, "No Steam profile for $steamId")
        return SteamProfile(steamId = player.steamid, displayName = player.personaname)
    }

    private fun ensureConfigured() {
        if (!isConfigured) {
            AppLogger.network.error("Steam Web API key is not configured (see Secrets.kt.example)")
            throw LibraryException(LibraryError.UNAVAILABLE, "STEAM_API_KEY missing")
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: LibraryException) {
        throw e
    } catch (t: Throwable) {
        throw t.toLibraryException().also { AppLogger.network.warn("Steam Web API call failed: ${t::class.simpleName}: ${t.message}") }
    }

    companion object {
        fun headerImageUrl(appId: Long) = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
    }
}
