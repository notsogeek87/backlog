package com.davidgcd.backlog.data.library

import com.davidgcd.backlog.data.local.GameSourceDao
import com.davidgcd.backlog.data.local.GameSourceEntity
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.data.local.gameStatus

/** A store that can list the ids of the games a user wishes for. Ids only — names come from IGDB. */
interface WishlistSource {
    /** @throws LibraryException with a user-mappable [LibraryError]. */
    suspend fun fetchWishlist(accountId: String): List<String>
}

data class WishlistSyncResult(
    /** Wishlisted games Steam returned. */
    val total: Int,
    /** Created in the backlog with the [GameStatus.WISHLIST] status. */
    val added: Int,
    /** Came back on the wishlist after having left it: restored from the archive. */
    val restored: Int,
    /** Left the wishlist and is now owned: promoted to [GameStatus.BACKLOG]. */
    val promoted: Int,
    /** Left the wishlist and isn't owned: archived (reversible), never deleted. */
    val archived: Int,
    /** IGDB has no game for these Steam ids (software, DLC, removed apps). */
    val unmatched: Int,
)

/**
 * One-way mirror of the Steam wishlist into the backlog (Steam's API offers no way to write to it).
 * Wishlisted games are stored with [GameStatus.WISHLIST] and tracked by `steam_wishlist` rows in
 * `game_sources`. Invariants: never deletes a game, never touches a game the user already had with
 * another status, and never reacts to an empty answer (a private or failed read isn't "removed all").
 */
class WishlistSyncService(
    private val source: WishlistSource,
    private val catalog: GameCatalog,
    private val repository: BacklogRepository,
    private val sourceDao: GameSourceDao,
    private val accounts: LibraryAccountStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun sync(): WishlistSyncResult {
        val account = accounts.get(LibraryProviders.STEAM) ?: throw LibraryException(LibraryError.NOT_CONNECTED)
        val appIds = source.fetchWishlist(account.accountId).distinct()
        if (appIds.isEmpty()) return WishlistSyncResult(0, 0, 0, 0, 0, 0)
        val now = clock()

        val igdbByApp = catalog.resolveExternalIds(STEAM_SOURCE_ID, appIds)
        val games = repository.allGames().associateBy { it.igdbId }
        val previous = sourceDao.forProvider(LibraryProviders.STEAM_WISHLIST).associateBy { it.externalId }
        val ownedIgdbIds = sourceDao.forProvider(LibraryProviders.STEAM).map { it.igdbId }.toSet()

        val wantedIds = appIds.mapNotNull { igdbByApp[it] }.toSet()
        val toCreate = wantedIds.filter { it !in games }
        val fetched = HashMap<Long, Game>()
        toCreate.chunked(FETCH_CHUNK).forEach { chunk -> catalog.getGamesByIds(chunk).forEach { fetched[it.id] = it } }

        var added = 0
        var restored = 0
        val writes = mutableListOf<GameSourceEntity>()
        for (appId in appIds) {
            val igdbId = igdbByApp[appId] ?: continue
            val existing = games[igdbId]
            when {
                existing == null -> {
                    val game = fetched[igdbId] ?: continue // IGDB hiccup: picked up by the next sync
                    repository.addToBacklog(game, GameStatus.WISHLIST)
                    // IGDB may not list the Steam page: the wishlist itself knows the app id (French blurb on the detail screen).
                    appId.toLongOrNull()?.let { id -> repository.findEntity(igdbId)?.let { repository.updateSteamAppId(it, id) } }
                    added++
                }
                previous[appId]?.missingFromLibrary == true && existing.gameStatus == GameStatus.WISHLIST && existing.isArchived -> {
                    repository.setArchived(existing, false)
                    restored++
                }
            }
            writes += GameSourceEntity(LibraryProviders.STEAM_WISHLIST, appId, igdbId, lastSyncedAt = now)
        }

        var promoted = 0
        var archived = 0
        val current = appIds.toSet()
        for (old in previous.values) {
            if (old.externalId in current || old.missingFromLibrary) continue
            writes += old.copy(missingFromLibrary = true, lastSyncedAt = now)
            val game = games[old.igdbId] ?: continue // the user removed it themselves
            if (game.gameStatus != GameStatus.WISHLIST) continue // the user moved on: hands off
            if (old.igdbId in ownedIgdbIds) {
                repository.setStatus(game, GameStatus.BACKLOG)
                promoted++
            } else {
                repository.setArchived(game, true)
                archived++
            }
        }

        if (writes.isNotEmpty()) sourceDao.upsertAll(writes)
        return WishlistSyncResult(
            total = appIds.size,
            added = added,
            restored = restored,
            promoted = promoted,
            archived = archived,
            unmatched = appIds.count { it !in igdbByApp },
        )
    }

    private companion object {
        /** IGDB `external_game_sources`: 1 = Steam. */
        const val STEAM_SOURCE_ID = 1
        const val FETCH_CHUNK = 100
    }
}
