package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.local.GameDao
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.model.DiscoverCategory
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.data.local.GameJsonCache
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.model.Genre
import com.davidgcd.backlog.model.Platform
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for backlog data: Room locally, IGDB for search /
 * refresh. Equivalent role to BacklogViewModel + GameSyncApplier on the iOS
 * app, minus CloudKit sync (local-only for now, see README).
 */
class BacklogRepository(
    private val gameDao: GameDao,
    private val igdbService: IgdbService,
    private val moshi: Moshi,
) {
    fun observeBacklog(): Flow<List<GameEntity>> = gameDao.observeAll()

    suspend fun allGames(): List<GameEntity> = gameDao.allGames()

    fun observeGame(igdbId: Long): Flow<GameEntity?> = gameDao.observeById(igdbId)

    suspend fun activeGamesWithReleaseDate(): List<GameEntity> = gameDao.activeGamesWithReleaseDate()

    suspend fun activeGames(): List<GameEntity> = gameDao.activeGames()

    suspend fun findEntity(igdbId: Long): GameEntity? = gameDao.findById(igdbId)

    suspend fun gameUrls(ids: List<Long>): Map<Long, String> = igdbService.getGameUrls(ids)

    suspend fun searchGames(query: String): List<Game> = igdbService.searchGames(query, partial = true)

    suspend fun getPopularGames(limit: Int = 20): List<Game> = igdbService.getPopularGames(limit)

    suspend fun getDiscoverGames(category: DiscoverCategory, limit: Int = 20): List<Game> =
        igdbService.getDiscoverGames(category, limit)

    /** Falls back to IGDB when the game isn't (or isn't yet) in the backlog. */
    suspend fun fetchRemoteGame(igdbId: Long): Game? = igdbService.getGame(igdbId)

    suspend fun addToBacklog(game: Game) {
        if (gameDao.findById(game.id) != null) return
        gameDao.upsert(game.toEntity(moshi))
    }

    suspend fun setArchived(entity: GameEntity, archived: Boolean) {
        gameDao.update(entity.copy(isArchived = archived))
    }

    suspend fun setStatus(entity: GameEntity, status: GameStatus) {
        gameDao.update(entity.copy(status = status.name))
    }

    suspend fun remove(entity: GameEntity) {
        gameDao.delete(entity)
    }

    private val genresListType = Types.newParameterizedType(List::class.java, Genre::class.java)
    private val platformsListType = Types.newParameterizedType(List::class.java, Platform::class.java)

    private fun Game.toEntity(moshi: Moshi): GameEntity {
        val genresJson = genres?.let { moshi.adapter<List<Genre>>(genresListType).toJson(it) }
        val platformsJson = platforms?.let { moshi.adapter<List<Platform>>(platformsListType).toJson(it) }
        return GameEntity(
            igdbId = id,
            name = name,
            coverImageId = cover?.imageId,
            firstReleaseDate = firstReleaseDate,
            genresJson = genresJson,
            platformsJson = platformsJson,
            summary = summary,
            totalRating = totalRating,
            steamAppId = steamAppId,
        )
    }

    /** Backfills steamAppId on an entity added before the Steam link was extracted, or if IGDB added one since. */
    suspend fun updateSteamAppId(entity: GameEntity, steamAppId: Long) {
        if (entity.steamAppId == steamAppId) return
        gameDao.update(entity.copy(steamAppId = steamAppId))
    }

    /**
     * Re-fetches one backlog entry from IGDB, persists whatever changed, and
     * reports a date change / newly-seen platforms — the data half of the
     * iOS app's SyncDriftDispatcher (notification dispatch is the caller's
     * job, same separation as `reconcileDateChange`/`reconcilePlatforms`
     * there). The baseline is always the entity as it stood *before* this
     * call, so a repeated run never re-reports the same change twice.
     */
    suspend fun refreshAndDetectDrift(entity: GameEntity): DriftResult? {
        val fresh = igdbService.getGame(entity.igdbId) ?: return null

        val oldPlatformNames = GameJsonCache.platformNames(entity).toSet()
        val newPlatformNames = fresh.platforms?.map { it.name }?.toSet() ?: emptySet()
        val addedPlatforms = (newPlatformNames - oldPlatformNames).toList()
        val dateChanged = entity.firstReleaseDate != null && entity.firstReleaseDate != fresh.firstReleaseDate

        val updated = fresh.toEntity(moshi).copy(
            isArchived = entity.isArchived,
            status = entity.status,
            addedAt = entity.addedAt,
            steamAppId = fresh.steamAppId ?: entity.steamAppId,
        )
        if (updated != entity) gameDao.update(updated)

        return if (dateChanged || addedPlatforms.isNotEmpty()) {
            DriftResult(dateChanged = dateChanged, newPlatforms = addedPlatforms)
        } else {
            null
        }
    }
}

data class DriftResult(val dateChanged: Boolean, val newPlatforms: List<String>)
