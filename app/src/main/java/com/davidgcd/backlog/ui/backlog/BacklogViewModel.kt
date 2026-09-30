package com.davidgcd.backlog.ui.backlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.GameJsonCache
import com.davidgcd.backlog.data.local.gameStatus
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.model.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns backlog state for the app's main screen. Equivalent role to
 * BacklogViewModel on iOS: the local list (sorted/filtered the same way
 * BacklogViewModel.releasePartition/filterStateToken drive the iOS grid),
 * a search query against IGDB, and add/archive/remove.
 */
/** Coarse search failure kinds; the screen maps them to localized copy. */
enum class SearchError { Network, Server, Unknown }

class BacklogViewModel(private val repository: BacklogRepository) : ViewModel() {

    private val backlog: StateFlow<List<GameEntity>> = repository.observeBacklog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sort = MutableStateFlow(BacklogSort.RECENTLY_ADDED)
    val sort: StateFlow<BacklogSort> = _sort

    private val _filter = MutableStateFlow(BacklogFilter())
    val filter: StateFlow<BacklogFilter> = _filter

    /** IGDB ids already saved, so search results can show "already added" instead of a dead Add button. */
    val backlogIds: StateFlow<Set<Long>> = backlog
        .map { list -> list.map { it.igdbId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Real counts for the header stat tiles (active vs archived), never narrowed by filters. */
    val activeCount: StateFlow<Int> = backlog
        .map { list -> list.count { !it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val archivedCount: StateFlow<Int> = backlog
        .map { list -> list.count { it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Newest additions for the home carousel. */
    val recentlyAdded: StateFlow<List<GameEntity>> = backlog
        .map { list -> list.filter { !it.isArchived }.sortedByDescending { it.addedAt }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The unfiltered count backs the empty-library check, same rule as the iOS app's `games.isEmpty`. */
    val isBacklogEmpty: StateFlow<Boolean> = backlog
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Options for the filter menus — derived from the raw backlog, never narrowed by the current filter. */
    val availableGenres: StateFlow<List<String>> = backlog
        .map { list -> list.flatMap { GameJsonCache.genreNames(it) }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availablePlatforms: StateFlow<List<String>> = backlog
        .map { list -> list.flatMap { GameJsonCache.platformNames(it) }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visibleBacklog: StateFlow<List<GameEntity>> = combine(backlog, _sort, _filter) { list, sort, filter ->
        list
            .filter { entity -> filter.showArchived || !entity.isArchived }
            .filter { entity -> filter.status == null || entity.gameStatus == filter.status }
            .filter { entity -> filter.genre == null || GameJsonCache.genreNames(entity).contains(filter.genre) }
            .filter { entity -> filter.platform == null || GameJsonCache.platformNames(entity).contains(filter.platform) }
            .sortedWith(sort.comparator())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSort(sort: BacklogSort) {
        _sort.value = sort
    }

    fun setFilter(filter: BacklogFilter) {
        _filter.value = filter
    }

    private val _searchResults = MutableStateFlow<List<Game>>(emptyList())
    val searchResults: StateFlow<List<Game>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _searchError = MutableStateFlow<SearchError?>(null)
    val searchError: StateFlow<SearchError?> = _searchError

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _isSearching.value = true
            _searchError.value = null
            try {
                _searchResults.value = repository.searchGames(query)
            } catch (t: CancellationException) {
                throw t
            } catch (t: retrofit2.HttpException) {
                // The raw IGDB body stays in the debug log; the UI shows a readable message.
                _searchError.value = SearchError.Server
            } catch (t: java.io.IOException) {
                _searchError.value = SearchError.Network
            } catch (t: Throwable) {
                _searchError.value = SearchError.Unknown
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

private fun BacklogSort.comparator(): Comparator<GameEntity> = when (this) {
    BacklogSort.RECENTLY_ADDED -> compareByDescending { it.addedAt }
    BacklogSort.NAME -> compareBy { it.name.lowercase() }
    BacklogSort.RELEASE_DATE -> nullsLastComparator(descending = false) { it.firstReleaseDate }
    BacklogSort.RATING -> nullsLastComparator(descending = true) { it.totalRating }
}

/** `nulls last` regardless of direction — a game with no rating/date shouldn't lead either sort. */
private fun <T : Comparable<T>> nullsLastComparator(descending: Boolean, selector: (GameEntity) -> T?): Comparator<GameEntity> =
    Comparator { a, b ->
        val left = selector(a)
        val right = selector(b)
        when {
            left == null && right == null -> 0
            left == null -> 1
            right == null -> -1
            descending -> right.compareTo(left)
            else -> left.compareTo(right)
        }
    }
