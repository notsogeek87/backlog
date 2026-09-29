package com.davidgcd.backlog.ui.gamedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.MetacriticScore
import com.davidgcd.backlog.data.repository.MetacriticService
import com.davidgcd.backlog.data.repository.SteamReviewSummary
import com.davidgcd.backlog.data.repository.SteamService
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.util.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
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
 * Steam and Metacritic never gate on each other and never surface an error —
 * same "no card, no message" rule as the iOS app's Notes section: a missing
 * score just means the corresponding half of RatingsState stays null.
 */
data class RatingsState(
    val isLoading: Boolean = false,
    val steam: SteamReviewSummary? = null,
    val metacritic: MetacriticScore? = null,
) {
    val isEmpty: Boolean get() = !isLoading && steam == null && metacritic == null
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
    private val steamService: SteamService,
    private val metacriticService: MetacriticService,
) : ViewModel() {

    private val _state = MutableStateFlow<GameDetailState>(GameDetailState.Loading)
    val state: StateFlow<GameDetailState> = _state

    private val _ratings = MutableStateFlow(RatingsState())
    val ratings: StateFlow<RatingsState> = _ratings

    /** French Steam blurb replacing IGDB's English-only summary; null keeps the IGDB one. */
    private val _frenchSummary = MutableStateFlow<String?>(null)
    val frenchSummary: StateFlow<String?> = _frenchSummary

    init {
        repository.observeGame(gameId)
            .onEach { entity ->
                if (entity != null) {
                    _state.value = GameDetailState.InBacklog(entity)
                    loadRatings(name = entity.name, knownSteamAppId = entity.steamAppId, backfillEntity = entity)
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
            val remote = try {
                repository.fetchRemoteGame(gameId)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                DebugLog.log("GameDetailViewModel: fetchRemoteGame($gameId) failed: ${t::class.simpleName}: ${t.message}")
                null
            }
            if (remote != null) {
                _state.value = GameDetailState.Remote(remote)
                loadRatings(name = remote.name, knownSteamAppId = remote.steamAppId, backfillEntity = null)
            } else {
                _state.value = GameDetailState.NotFound
            }
        }
    }

    private var ratingsLoadStarted = false

    private fun loadRatings(name: String, knownSteamAppId: Long?, backfillEntity: GameEntity?) {
        if (ratingsLoadStarted) return
        ratingsLoadStarted = true
        _ratings.value = RatingsState(isLoading = true)
        viewModelScope.launch {
            // A backlog entity added before the Steam link was extracted has no steamAppId yet —
            // backfill it from a fresh IGDB fetch, mirroring the iOS app's dual-write on this path.
            val steamAppId = knownSteamAppId ?: backfillEntity?.let { entity ->
                try {
                    repository.fetchRemoteGame(gameId)?.steamAppId?.also { repository.updateSteamAppId(entity, it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    DebugLog.log("GameDetailViewModel: steamAppId backfill($gameId) failed: ${t::class.simpleName}: ${t.message}")
                    null
                }
            }

            val steamDeferred = async { steamAppId?.let { steamService.reviewSummary(it) } }
            launch { steamAppId?.let { _frenchSummary.value = steamService.frenchDescription(it) } }
            val metacriticDeferred = async { metacriticService.scoreFor(name) }

            _ratings.value = RatingsState(
                isLoading = false,
                steam = steamDeferred.await(),
                metacritic = metacriticDeferred.await(),
            )
        }
    }

    fun addToBacklog(game: Game) {
        viewModelScope.launch { repository.addToBacklog(game) }
    }

    fun setArchived(entity: GameEntity, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(entity, archived) }
    }

    /** [onDone] runs once the row is deleted, so leaving the screen can't cancel the delete mid-flight. */
    fun remove(entity: GameEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.remove(entity)
            onDone()
        }
    }
}

class GameDetailViewModelFactory(
    private val gameId: Long,
    private val repository: BacklogRepository,
    private val steamService: SteamService,
    private val metacriticService: MetacriticService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == GameDetailViewModel::class.java)
        return GameDetailViewModel(gameId, repository, steamService, metacriticService) as T
    }
}
