package com.davidgcd.backlog.ui.backlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.model.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns backlog state for the app's main screen. Equivalent role to
 * BacklogViewModel on iOS, trimmed down to what the skeleton needs:
 * the local list, a search query against IGDB, and add/archive/remove.
 */
class BacklogViewModel(private val repository: BacklogRepository) : ViewModel() {

    val backlog: StateFlow<List<GameEntity>> = repository.observeBacklog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchResults = MutableStateFlow<List<Game>>(emptyList())
    val searchResults: StateFlow<List<Game>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            try {
                _searchResults.value = repository.searchGames(query)
            } catch (t: Throwable) {
                _searchError.value = t.message ?: "Search failed"
            } finally {
                _isSearching.value = false
            }
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
