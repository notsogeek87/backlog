package com.davidgcd.backlog.data.library

import com.davidgcd.backlog.data.local.GameSourceDao
import com.davidgcd.backlog.data.local.GameSourceEntity
import com.davidgcd.backlog.model.Game
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeGameSourceDao : GameSourceDao {
    val rows = MutableStateFlow<List<GameSourceEntity>>(emptyList())

    override suspend fun forProvider(provider: String) = rows.value.filter { it.provider == provider }

    override fun observeForGame(igdbId: Long): Flow<List<GameSourceEntity>> = rows.map { list -> list.filter { it.igdbId == igdbId } }

    override suspend fun upsertAll(sources: List<GameSourceEntity>) {
        val keys = sources.map { it.provider to it.externalId }.toSet()
        rows.value = rows.value.filterNot { (it.provider to it.externalId) in keys } + sources
    }

    override suspend fun deleteForProvider(provider: String) {
        rows.value = rows.value.filterNot { it.provider == provider }
    }
}

class FakeAccountStore : LibraryAccountStore {
    private val accounts = MutableStateFlow<Map<String, LibraryAccount>>(emptyMap())

    override fun observe(providerId: String) = accounts.map { it[providerId] }

    override suspend fun connect(providerId: String, accountId: String, displayName: String?, now: Long) {
        accounts.value = accounts.value + (providerId to LibraryAccount(providerId, accountId, displayName, connectedAt = now))
    }

    override suspend fun disconnect(providerId: String) {
        accounts.value = accounts.value - providerId
    }

    override suspend fun recordSync(providerId: String, syncedAt: Long, ownedCount: Int, alreadyInBacklogCount: Int, newCount: Int) {
        accounts.value[providerId]?.let {
            accounts.value = accounts.value + (providerId to it.copy(
                lastSyncedAt = syncedAt, ownedCount = ownedCount,
                alreadyInBacklogCount = alreadyInBacklogCount, newCount = newCount,
            ))
        }
    }
}

class FakeLibraryProvider(
    var library: List<LibraryGame> = emptyList(),
    var failure: LibraryException? = null,
) : GameLibraryProvider {
    override val id = LibraryProviders.STEAM
    override val igdbExternalSourceId = 1
    override suspend fun fetchLibrary(accountId: String): List<LibraryGame> {
        failure?.let { throw it }
        return library
    }
    override fun existingLinkId(game: com.davidgcd.backlog.data.local.GameEntity) = game.steamAppId?.toString()
}

/** [external] = Steam AppID → IGDB id; [games] = the IGDB catalogue (also what title search hits). */
class FakeCatalog(
    var external: Map<String, Long> = emptyMap(),
    var games: List<Game> = emptyList(),
) : GameCatalog {
    var searchCalls = 0
    override suspend fun resolveExternalIds(externalSourceId: Int, externalIds: List<String>) =
        external.filterKeys { it in externalIds }

    override suspend fun getGamesByIds(ids: List<Long>) = games.filter { it.id in ids }

    override suspend fun searchGames(query: String, limit: Int): List<Game> {
        searchCalls++
        return games.filter { it.name.contains(query.substringBefore(':').trim(), ignoreCase = true) || query.contains(it.name, ignoreCase = true) }
    }
}
