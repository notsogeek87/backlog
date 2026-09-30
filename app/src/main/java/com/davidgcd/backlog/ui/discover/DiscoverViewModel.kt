package com.davidgcd.backlog.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.model.DiscoverCategory
import com.davidgcd.backlog.model.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
 * Games discovery (popular, top rated, trending, new, upcoming — see [DiscoverCategory]),
 * a simplified stand-in for the iOS app's PopularGamesLoader: one fetch per selected list, refreshed on demand
 * (no disk cache / stale-while-revalidate yet, see README). Backlog games
 * are filtered out client-side so "add" only ever offers something new.
 */
class DiscoverViewModel(private val repository: BacklogRepository) : ViewModel() {

    private val _state = MutableStateFlow<DiscoverState>(DiscoverState.Loading)
    val state: StateFlow<DiscoverState> = _state

    private val _category = MutableStateFlow(DiscoverCategory.POPULAR)
    val category: StateFlow<DiscoverCategory> = _category

    private var loadJob: Job? = null

    val backlogIds: StateFlow<Set<Long>> = repository.observeBacklog()
        .combine(_state) { list, _ -> list.map { it.igdbId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        refresh()
    }

    fun select(category: DiscoverCategory) {
        if (_category.value == category) return
        _category.value = category
        refresh()
    }

    fun refresh() {
        // A slower response for a previously selected list must never overwrite the current one.
        loadJob?.cancel()
        val category = _category.value
        loadJob = viewModelScope.launch {
            _state.value = DiscoverState.Loading
            _state.value = try {
                DiscoverState.Loaded(repository.getDiscoverGames(category))
            } catch (t: CancellationException) {
                throw t
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
