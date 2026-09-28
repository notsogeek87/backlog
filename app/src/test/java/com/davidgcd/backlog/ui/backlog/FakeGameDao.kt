package com.davidgcd.backlog.ui.backlog

import com.davidgcd.backlog.data.local.GameDao
import com.davidgcd.backlog.data.local.GameEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory GameDao for ViewModel/repository tests — no Room, no Robolectric needed. */
class FakeGameDao(initial: List<GameEntity> = emptyList()) : GameDao {
    private val state = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<GameEntity>> = state

    override suspend fun findById(igdbId: Long): GameEntity? = state.value.firstOrNull { it.igdbId == igdbId }

    override fun observeById(igdbId: Long): Flow<GameEntity?> = state.map { list -> list.firstOrNull { it.igdbId == igdbId } }

    override suspend fun activeGamesWithReleaseDate(): List<GameEntity> =
        state.value.filter { !it.isArchived && it.firstReleaseDate != null }

    override suspend fun activeGames(): List<GameEntity> = state.value.filter { !it.isArchived }

    override suspend fun allGames(): List<GameEntity> = state.value

    override suspend fun upsert(game: GameEntity) {
        state.value = state.value.filterNot { it.igdbId == game.igdbId } + game
    }

    override suspend fun update(game: GameEntity) {
        state.value = state.value.map { if (it.igdbId == game.igdbId) game else it }
    }

    override suspend fun delete(game: GameEntity) {
        state.value = state.value.filterNot { it.igdbId == game.igdbId }
    }

    override suspend fun activeCount(): Int = state.value.count { !it.isArchived }
}
