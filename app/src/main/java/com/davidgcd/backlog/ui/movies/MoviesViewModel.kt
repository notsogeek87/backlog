package com.davidgcd.backlog.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.local.genreList
import com.davidgcd.backlog.data.local.titleKind
import com.davidgcd.backlog.data.local.watchStatus
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.model.ImdbTitle
import com.davidgcd.backlog.model.MovieRanking
import com.davidgcd.backlog.model.TitleKind
import com.davidgcd.backlog.model.WatchStatus
import com.davidgcd.backlog.ui.backlog.SearchError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MovieSort {
    RECENTLY_ADDED,
    NAME,
    RELEASE_DATE,
    IMDB_RATING,
    MY_RATING,
    MY_RANKING,
}

/** `null` = any. [kind] is driven by the Tous/Films/Séries chips, the rest by the filter menu. */
data class MovieFilter(
    val showArchived: Boolean = false,
    val kind: TitleKind? = null,
    val status: WatchStatus? = null,
    val genre: String? = null,
) {
    /** Menu filters only — the kind chips are always visible, so they never need a "clear" chip. */
    val isActive: Boolean get() = showArchived || status != null || genre != null
}

/** State of the films & séries tab: the saved list (sorted / filtered) and the IMDb search. */
class MoviesViewModel(private val repository: MovieRepository) : ViewModel() {

    private val movies: StateFlow<List<MovieEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sort = MutableStateFlow(MovieSort.RECENTLY_ADDED)
    val sort: StateFlow<MovieSort> = _sort

    private val _filter = MutableStateFlow(MovieFilter())
    val filter: StateFlow<MovieFilter> = _filter

    val savedIds: StateFlow<Set<String>> = movies
        .map { list -> list.map { it.imdbId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Per-status counts for the header tiles: archived titles excluded, never narrowed by filters. */
    val statusCounts: StateFlow<Map<WatchStatus, Int>> = movies
        .map { list ->
            val active = list.filter { !it.isArchived }
            WatchStatus.entries.associateWith { status -> active.count { it.watchStatus == status } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchStatus.entries.associateWith { 0 })

    val recentlyAdded: StateFlow<List<MovieEntity>> = movies
        .map { list -> list.filter { !it.isArchived }.sortedByDescending { it.addedAt }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isEmpty: StateFlow<Boolean> = movies
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val availableGenres: StateFlow<List<String>> = movies
        .map { list -> list.flatMap { it.genreList }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visible: StateFlow<List<MovieEntity>> = combine(movies, _sort, _filter) { list, sort, filter ->
        list
            .filter { filter.showArchived || !it.isArchived }
            .filter { filter.kind == null || it.titleKind == filter.kind }
            .filter { filter.status == null || it.watchStatus == filter.status }
            .filter { filter.genre == null || it.genreList.contains(filter.genre) }
            .sortedWith(sort.comparator())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Imported titles have no poster yet: fill them in a few at a time while the tab is open.
        viewModelScope.launch {
            repository.observeAll()
                .map { list -> list.count { it.posterUrl == null } }
                .distinctUntilChanged()
                .collectLatest { missing -> if (missing > 0) repository.backfillPosters() }
        }
    }

    fun setSort(sort: MovieSort) {
        _sort.value = sort
    }

    fun setFilter(filter: MovieFilter) {
        _filter.value = filter
    }

    /** What a plain-text share lists: every non-archived title, ranked ones first (never narrowed by the filter). */
    suspend fun titlesToShare(): List<MovieEntity> = MovieRanking.order(repository.allMovies())

    // --- search ------------------------------------------------------------------------------

    private val _searchResults = MutableStateFlow<List<ImdbTitle>>(emptyList())
    val searchResults: StateFlow<List<ImdbTitle>> = _searchResults

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
            _searchError.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _isSearching.value = true
            _searchError.value = null
            try {
                _searchResults.value = repository.search(query)
            } catch (t: CancellationException) {
                throw t
            } catch (t: java.io.IOException) {
                _searchError.value = SearchError.Network
            } catch (t: com.davidgcd.backlog.data.imdb.ImdbException) {
                // The client wraps I/O failures: an offline phone is a network problem, an odd answer is IMDb's.
                _searchError.value = if (t.cause is java.io.IOException) SearchError.Network else SearchError.Server
            } catch (t: Throwable) {
                _searchError.value = SearchError.Unknown
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun add(title: ImdbTitle) {
        viewModelScope.launch { repository.add(title) }
    }

    fun setArchived(movie: MovieEntity, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(movie, archived) }
    }
}

private const val SECONDS_PER_YEAR = 31_556_952L

private fun MovieSort.comparator(): Comparator<MovieEntity> = when (this) {
    MovieSort.RECENTLY_ADDED -> compareByDescending { it.addedAt }
    MovieSort.NAME -> compareBy { it.title.lowercase() }
    MovieSort.RELEASE_DATE -> nullsLast(descending = true) { it.releaseDate ?: it.year?.let { y -> (y - 1970) * SECONDS_PER_YEAR } }
    MovieSort.IMDB_RATING -> nullsLast(descending = true) { it.imdbRating }
    MovieSort.MY_RATING -> nullsLast(descending = true) { it.userRating }
    MovieSort.MY_RANKING -> nullsLast(descending = false) { it.userRank }.thenBy { it.addedAt }
}

/** `nulls last` whatever the direction — a title with no rating/date shouldn't lead either sort. */
private fun <T : Comparable<T>> nullsLast(descending: Boolean, selector: (MovieEntity) -> T?): Comparator<MovieEntity> =
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

class MoviesViewModelFactory(private val repository: MovieRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == MoviesViewModel::class.java)
        return MoviesViewModel(repository) as T
    }
}
