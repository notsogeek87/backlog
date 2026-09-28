package com.davidgcd.backlog.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.model.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DiscoverState {
    data object Loading : DiscoverState
    data class Loaded(val games: List<Game>) : DiscoverState
    data object Error : DiscoverState
}

/**
 * Popular-games discovery, a simplified stand-in for the iOS app's
 * PopularGamesLoader: one fetch shared by the screen, refreshed on demand
 * (no disk cache / stale-while-revalidate yet, see README). Backlog games
 * are filtered out client-side so "add" only ever offers something new.
 */
class DiscoverViewModel(private val repository: BacklogRepository) : ViewModel() {

    private val _state = MutableStateFlow<DiscoverState>(DiscoverState.Loading)
    val state: StateFlow<DiscoverState> = _state

    val backlogIds: StateFlow<Set<Long>> = repository.observeBacklog()
        .combine(_state) { list, _ -> list.map { it.igdbId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = DiscoverState.Loading
            _state.value = try {
                DiscoverState.Loaded(repository.getPopularGames())
            } catch (t: Throwable) {
                DiscoverState.Error
            }
        }
    }

    fun addToBacklog(game: Game) {
        viewModelScope.launch { repository.addToBacklog(game) }
    }
}

class DiscoverViewModelFactory(private val repository: BacklogRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == DiscoverViewModel::class.java)
        return DiscoverViewModel(repository) as T
    }
}
