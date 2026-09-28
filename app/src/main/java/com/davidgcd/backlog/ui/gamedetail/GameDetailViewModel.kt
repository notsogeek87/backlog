package com.davidgcd.backlog.ui.gamedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.model.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed interface GameDetailState {
    data object Loading : GameDetailState
    data class InBacklog(val entity: GameEntity) : GameDetailState
    data class Remote(val game: Game) : GameDetailState
    data object NotFound : GameDetailState
}

/**
 * A game viewed from the detail screen may or may not be in the backlog —
 * a recently-viewed or search-result game has neither a GameEntity row nor
 * any local id, the same split the iOS app's GameDetailView/HomeRoute.game
 * handles by resolving through a scoped query or an IGDB fetch.
 */
class GameDetailViewModel(
    private val gameId: Long,
    private val repository: BacklogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<GameDetailState>(GameDetailState.Loading)
    val state: StateFlow<GameDetailState> = _state

    init {
        repository.observeGame(gameId)
            .onEach { entity ->
                if (entity != null) {
                    _state.value = GameDetailState.InBacklog(entity)
                } else if (_state.value !is GameDetailState.InBacklog) {
                    loadRemoteIfNeeded()
                }
            }
            .launchIn(viewModelScope)
    }

    private var remoteLoadStarted = false

    private fun loadRemoteIfNeeded() {
        if (remoteLoadStarted) return
        remoteLoadStarted = true
        viewModelScope.launch {
            val remote = repository.fetchRemoteGame(gameId)
            _state.value = if (remote != null) GameDetailState.Remote(remote) else GameDetailState.NotFound
        }
    }

    fun addToBacklog(game: Game) {
        viewModelScope.launch { repository.addToBacklog(game) }
    }

    fun setArchived(entity: GameEntity, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(entity, archived) }
    }

    fun remove(entity: GameEntity) {
        viewModelScope.launch { repository.remove(entity) }
    }
}

class GameDetailViewModelFactory(
    private val gameId: Long,
    private val repository: BacklogRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == GameDetailViewModel::class.java)
        return GameDetailViewModel(gameId, repository) as T
    }
}
