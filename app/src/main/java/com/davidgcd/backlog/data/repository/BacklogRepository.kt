package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.local.GameDao
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.model.Game
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
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

    private fun Game.toEntity(moshi: Moshi): GameEntity {
        val genresJson = genres?.let { moshi.adapter<List<com.davidgcd.backlog.model.Genre>>().toJson(it) }
        val platformsJson = platforms?.let { moshi.adapter<List<com.davidgcd.backlog.model.Platform>>().toJson(it) }
        return GameEntity(
            igdbId = id,
            name = name,
            coverImageId = cover?.imageId,
            firstReleaseDate = firstReleaseDate,
            genresJson = genresJson,
            platformsJson = platformsJson,
            summary = summary,
            totalRating = totalRating,
        )
    }
}
