package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.local.GameDao
import com.davidgcd.backlog.data.local.GameEntity
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

    fun observeGame(igdbId: Long): Flow<GameEntity?> = gameDao.observeById(igdbId)

    suspend fun activeGamesWithReleaseDate(): List<GameEntity> = gameDao.activeGamesWithReleaseDate()

    suspend fun searchGames(query: String): List<Game> = igdbService.searchGames(query)

    /** Falls back to IGDB when the game isn't (or isn't yet) in the backlog. */
    suspend fun fetchRemoteGame(igdbId: Long): Game? = igdbService.getGame(igdbId)

    suspend fun addToBacklog(game: Game) {
        if (gameDao.findById(game.id) != null) return
        gameDao.upsert(game.toEntity(moshi))
    }

    suspend fun setArchived(entity: GameEntity, archived: Boolean) {
        gameDao.update(entity.copy(isArchived = archived))
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
}
