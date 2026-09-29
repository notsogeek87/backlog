package com.davidgcd.backlog.data.library

import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.GameSourceDao
import com.davidgcd.backlog.data.local.GameSourceEntity
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.util.AppLogger

enum class ItemStatus {
    /** Already linked to this store from a previous sync. */
    LINKED,

    /** In the backlog, matched with certainty (id or identical title): linked automatically. */
    EXISTING,

    /** Not in the backlog yet — importable. */
    NEW,

    /** A plausible but not certain match: only applied if the user accepts it. */
    UNCERTAIN,

    /** IGDB doesn't know it (tools, soundtracks…) — can't live in the backlog, which is keyed by IGDB id. */
    UNMATCHED,
}

data class LibraryImportItem(
    val game: LibraryGame,
    val status: ItemStatus,
    /** IGDB id to add / link to: the match for NEW/EXISTING/LINKED, the *proposed* one for UNCERTAIN. */
    val igdbId: Long? = null,
    /** Name of that match, shown next to an UNCERTAIN item. */
    val matchedName: String? = null,
    /** True when [igdbId] is already a backlog game (accepting only adds the source, no new game). */
    val candidateInBacklog: Boolean = false,
    /** IGDB data already fetched during matching, saved from a second lookup on import. */
    val remoteGame: Game? = null,
    /** The user removed it from the backlog earlier — offered again but not pre-selected. */
    val previouslyRemoved: Boolean = false,
) {
    val key: String get() = game.externalId
    val isImportable: Boolean get() = (status == ItemStatus.NEW || status == ItemStatus.UNCERTAIN) && igdbId != null
}

data class SyncPreview(
    val providerId: String,
    val items: List<LibraryImportItem>,
    val syncedAt: Long,
    /** Games still in the backlog that the store no longer returned (kept, just flagged). */
    val missingFromLibraryCount: Int = 0,
) {
    val ownedCount: Int get() = items.size
    val alreadyCount: Int get() = items.count { it.status == ItemStatus.LINKED || it.status == ItemStatus.EXISTING }
    val newCount: Int get() = items.count { it.status == ItemStatus.NEW }
    fun items(status: ItemStatus) = items.filter { it.status == status }
}

data class ImportResult(
    /** Games newly created in the backlog. */
    val added: Int,
    /** Games that were already there (linked or matched) — no duplicate was created for them. */
    val alreadyPresent: Int,
    val failedNames: List<String>,
)

data class LibraryProgress(val stage: Stage, val done: Int = 0, val total: Int = 0) {
    enum class Stage { FETCHING, MATCHING, IMPORTING }
}

/**
 * Provider-agnostic library sync: fetch → match against the backlog and IGDB → [SyncPreview], then
 * [import] applies the user's choices. Invariants: never deletes a game, never touches a backlog
 * game's own fields (only its `game_sources` row), never creates a game that already exists.
 */
class LibrarySyncService(
    private val providers: Map<String, GameLibraryProvider>,
    private val catalog: GameCatalog,
    private val repository: BacklogRepository,
    private val sourceDao: GameSourceDao,
    private val accounts: LibraryAccountStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun provider(providerId: String): GameLibraryProvider =
        providers[providerId] ?: throw LibraryException(LibraryError.UNKNOWN, "No provider registered for $providerId")

    /**
     * Fetches the current library, refreshes playtime of games already linked, links certain matches,
     * and returns what's left for the user to decide. Safe to run repeatedly.
     */
    suspend fun sync(providerId: String, onProgress: (LibraryProgress) -> Unit = {}): SyncPreview {
        val provider = provider(providerId)
        val account = accounts.get(providerId) ?: throw LibraryException(LibraryError.NOT_CONNECTED)

        onProgress(LibraryProgress(LibraryProgress.Stage.FETCHING))
        val library = provider.fetchLibrary(account.accountId).distinctBy { it.externalId }
        val now = clock()

        val games = repository.allGames()
        val gamesById = games.associateBy { it.igdbId }
        val sources = sourceDao.forProvider(providerId)
        val sourceByExternal = sources.associateBy { it.externalId }
        val gameByLegacyLink = games.mapNotNull { g -> provider.existingLinkId(g)?.let { it to g } }.toMap()

        val resolved = LinkedHashMap<String, LibraryImportItem>()
        val pending = mutableListOf<LibraryGame>()
        for (lg in library) {
            val source = sourceByExternal[lg.externalId]
            val linkedGame = source?.let { gamesById[it.igdbId] }
            when {
                linkedGame != null -> resolved[lg.externalId] =
                    LibraryImportItem(lg, ItemStatus.LINKED, linkedGame.igdbId, linkedGame.name, candidateInBacklog = true)
                source != null -> resolved[lg.externalId] =
                    LibraryImportItem(lg, ItemStatus.NEW, source.igdbId, previouslyRemoved = true)
                gameByLegacyLink[lg.externalId] != null -> gameByLegacyLink.getValue(lg.externalId).let {
                    resolved[lg.externalId] = LibraryImportItem(lg, ItemStatus.EXISTING, it.igdbId, it.name, candidateInBacklog = true)
                }
                else -> pending += lg
            }
        }

        // Store id → IGDB id in bulk (cheap, certain), before falling back to title matching.
        val externalMap = provider.igdbExternalSourceId?.let { sourceId ->
            onProgress(LibraryProgress(LibraryProgress.Stage.MATCHING, 0, pending.size))
            catalog.resolveExternalIds(sourceId, pending.map { it.externalId })
        }.orEmpty()

        val byTitle = mutableListOf<LibraryGame>()
        for (lg in pending) {
            val igdbId = externalMap[lg.externalId]
            val local = igdbId?.let { gamesById[it] }
            when {
                local != null -> resolved[lg.externalId] =
                    LibraryImportItem(lg, ItemStatus.EXISTING, local.igdbId, local.name, candidateInBacklog = true)
                igdbId != null -> resolved[lg.externalId] = LibraryImportItem(lg, ItemStatus.NEW, igdbId)
                else -> byTitle += lg
            }
        }

        byTitle.forEachIndexed { index, lg ->
            onProgress(LibraryProgress(LibraryProgress.Stage.MATCHING, index, byTitle.size))
            resolved[lg.externalId] = matchByTitle(lg, games, gamesById)
        }

        val items = library.map { resolved.getValue(it.externalId) }

        // Persist only what is certain: refresh linked sources, link exact matches, flag vanished ones.
        val writes = mutableListOf<GameSourceEntity>()
        items.filter { it.status == ItemStatus.LINKED || it.status == ItemStatus.EXISTING }
            .forEach { writes += it.game.toSource(providerId, it.igdbId!!, now) }
        val seen = library.map { it.externalId }.toSet()
        var missing = 0
        // An empty answer could be a hiccup: don't flag the whole previous library as gone.
        if (library.isNotEmpty()) {
            sources.filter { it.externalId !in seen && gamesById.containsKey(it.igdbId) }.forEach {
                missing++
                if (!it.missingFromLibrary) writes += it.copy(missingFromLibrary = true)
            }
        }
        if (writes.isNotEmpty()) sourceDao.upsertAll(writes)

        val preview = SyncPreview(providerId, items, now, missing)
        accounts.recordSync(providerId, now, preview.ownedCount, preview.alreadyCount, preview.newCount)
        return preview
    }

    private suspend fun matchByTitle(lg: LibraryGame, games: List<GameEntity>, gamesById: Map<Long, GameEntity>): LibraryImportItem {
        GameMatcher.best(lg.name, games) { it.name }?.let { (local, confidence) ->
            return LibraryImportItem(
                lg,
                if (confidence == GameMatcher.Confidence.EXACT) ItemStatus.EXISTING else ItemStatus.UNCERTAIN,
                local.igdbId, local.name, candidateInBacklog = true,
            )
        }
        val hits = try {
            catalog.searchGames(lg.name, limit = 5)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            // One failed lookup shouldn't sink the whole sync: it stays "not found" until the next one.
            AppLogger.network.warn("Title lookup failed for \"${lg.name}\": ${t.message}")
            return LibraryImportItem(lg, ItemStatus.UNMATCHED)
        }
        val (hit, confidence) = GameMatcher.best(lg.name, hits) { it.name } ?: return LibraryImportItem(lg, ItemStatus.UNMATCHED)
        val local = gamesById[hit.id]
        return when {
            local != null && confidence == GameMatcher.Confidence.EXACT ->
                LibraryImportItem(lg, ItemStatus.EXISTING, local.igdbId, local.name, candidateInBacklog = true)
            else -> LibraryImportItem(
                lg,
                if (confidence == GameMatcher.Confidence.EXACT) ItemStatus.NEW else ItemStatus.UNCERTAIN,
                hit.id, hit.name, candidateInBacklog = local != null, remoteGame = hit,
            )
        }
    }

    /** Applies the user's selection ([selectedKeys] = store ids of the NEW / accepted UNCERTAIN items). */
    suspend fun import(
        providerId: String,
        preview: SyncPreview,
        selectedKeys: Set<String>,
        onProgress: (LibraryProgress) -> Unit = {},
    ): ImportResult {
        val provider = provider(providerId)
        val chosen = preview.items.filter { it.key in selectedKeys && it.isImportable }
        val now = clock()
        val existingIds = repository.allGames().map { it.igdbId }.toSet()

        val idsToFetch = chosen.filter { it.igdbId!! !in existingIds && it.remoteGame == null }.map { it.igdbId!! }.distinct()
        val fetched = HashMap<Long, Game>()
        idsToFetch.chunked(FETCH_CHUNK).forEachIndexed { index, chunk ->
            onProgress(LibraryProgress(LibraryProgress.Stage.IMPORTING, index * FETCH_CHUNK, idsToFetch.size))
            catalog.getGamesByIds(chunk).forEach { fetched[it.id] = it }
        }

        var added = 0
        var linkedOnly = 0
        val failed = mutableListOf<String>()
        val sources = mutableListOf<GameSourceEntity>()
        val createdIds = mutableSetOf<Long>()
        chosen.forEachIndexed { index, item ->
            val igdbId = item.igdbId!!
            onProgress(LibraryProgress(LibraryProgress.Stage.IMPORTING, index, chosen.size))
            val present = igdbId in existingIds || igdbId in createdIds
            if (!present) {
                val game = item.remoteGame ?: fetched[igdbId]
                if (game == null) {
                    failed += item.game.name
                    return@forEachIndexed
                }
                repository.addToBacklog(game) // no-ops if the id already exists: can't duplicate
                createdIds += igdbId
                added++
            } else linkedOnly++
            // Backfill the legacy column the game-detail screen reads (Steam reviews).
            repository.findEntity(igdbId)?.let { entity ->
                if (provider.existingLinkId(entity) == null) legacySteamId(providerId, item.key)?.let { repository.updateSteamAppId(entity, it) }
            }
            sources += item.game.toSource(providerId, igdbId, now)
        }
        if (sources.isNotEmpty()) sourceDao.upsertAll(sources)

        val succeeded = sources.map { it.externalId }.toSet()
        val newlyLinkedNew = chosen.count { it.status == ItemStatus.NEW && it.key in succeeded }
        val alreadyPresent = preview.alreadyCount + linkedOnly
        accounts.recordSync(
            providerId, preview.syncedAt, preview.ownedCount,
            alreadyInBacklogCount = alreadyPresent + added,
            newCount = (preview.newCount - newlyLinkedNew).coerceAtLeast(0),
        )
        return ImportResult(added, alreadyPresent, failed)
    }

    private fun legacySteamId(providerId: String, externalId: String): Long? =
        if (providerId == LibraryProviders.STEAM) externalId.toLongOrNull() else null

    private fun LibraryGame.toSource(providerId: String, igdbId: Long, now: Long) = GameSourceEntity(
        provider = providerId,
        externalId = externalId,
        igdbId = igdbId,
        playtimeMinutes = playtimeMinutes,
        recentPlaytimeMinutes = recentPlaytimeMinutes,
        lastSyncedAt = now,
    )

    private companion object {
        const val FETCH_CHUNK = 100
    }
}
