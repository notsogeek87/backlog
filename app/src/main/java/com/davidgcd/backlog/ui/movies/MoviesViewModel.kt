package com.davidgcd.backlog.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.local.genreList
import com.davidgcd.backlog.data.local.titleKind
import com.davidgcd.backlog.data.local.watchStatus
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.data.share.LibrarySharer
import com.davidgcd.backlog.data.share.ShareLinkService
import com.davidgcd.backlog.data.share.ShareMovieItem
import com.davidgcd.backlog.model.MediaTitle
import com.davidgcd.backlog.model.MovieRanking
import com.davidgcd.backlog.model.TitleKey
import com.davidgcd.backlog.model.TitleKind
import com.davidgcd.backlog.model.WatchStatus
import com.davidgcd.backlog.util.LibraryQuery
import com.davidgcd.backlog.ui.backlog.SearchError
import com.davidgcd.backlog.util.AppLogger
import com.davidgcd.backlog.util.TmdbImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MovieSort {
    RECENTLY_ADDED,
    NAME,
    RELEASE_DATE,
    TMDB_RATING,
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

/** State of the films & séries tab: the saved list (sorted / filtered) and the TMDB search. */
class MoviesViewModel(
    private val repository: MovieRepository,
    private val shareLinkService: ShareLinkService? = null,
    private val librarySharer: LibrarySharer? = null,
) : ViewModel() {

    private val movies: StateFlow<List<MovieEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sort = MutableStateFlow(MovieSort.RECENTLY_ADDED)
    val sort: StateFlow<MovieSort> = _sort

    private val _filter = MutableStateFlow(MovieFilter())
    val filter: StateFlow<MovieFilter> = _filter

    val savedIds: StateFlow<Set<String>> = movies
        .map { list -> list.map { it.titleKey }.toSet() }
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

    private val _libraryQuery = MutableStateFlow("")

    /** Texte tapé dans « Ma liste » : filtre instantané, accents et casse ignorés. */
    val libraryQuery: StateFlow<String> = _libraryQuery

    fun setLibraryQuery(query: String) {
        _libraryQuery.value = query
    }

    val visible: StateFlow<List<MovieEntity>> = combine(movies, _sort, _filter, _libraryQuery) { list, sort, filter, query ->
        list
            .filter { LibraryQuery.matches(query, it.title, it.directors, it.cast) }
            .filter { filter.showArchived || !it.isArchived }
            .filter { filter.kind == null || it.titleKind == filter.kind }
            .filter { filter.status == null || it.watchStatus == filter.status }
            .filter { filter.genre == null || it.genreList.contains(filter.genre) }
            .sortedWith(sort.comparator())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSort(sort: MovieSort) {
        _sort.value = sort
    }

    fun setFilter(filter: MovieFilter) {
        _filter.value = filter
    }

    /** What a plain-text share lists: every non-archived title, ranked ones first (never narrowed by the filter). */
    suspend fun titlesToShare(): List<MovieEntity> = MovieRanking.order(repository.allMovies())

    /**
     * Publishes the list as a public page (posters, best user note first) and returns its link, or null
     * when there is no server configured or it can't be reached — the caller then shares plain text.
     */
    suspend fun publishShareLink(title: String, movies: List<MovieEntity>): String? {
        val service = shareLinkService ?: return null
        val items = movies.filter { !it.isArchived }.map {
            ShareMovieItem(
                name = it.title,
                status = it.status,
                series = it.titleKind == TitleKind.SERIES,
                year = it.year,
                posterUrl = TmdbImage.poster(it.posterUrl, width = 342),
                url = TitleKey.url(it.titleKey),
                rating = it.userRating,
            )
        }
        return try {
            service.publishMovies(title, items)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            AppLogger.network.error("Share link: publishing the movies page failed, sharing plain text instead", t)
            null
        }
    }

    /** Link to the whole-library page (games + films & séries tabs), or null when unavailable. */
    suspend fun publishLibraryLink(title: String): String? = librarySharer?.publish(title)

    // --- search ------------------------------------------------------------------------------

    private val _searchResults = MutableStateFlow<List<MediaTitle>>(emptyList())
    val searchResults: StateFlow<List<MediaTitle>> = _searchResults

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
            } catch (t: com.davidgcd.backlog.data.tmdb.TmdbException) {
                // The client wraps I/O failures: an offline phone is a network problem, an odd answer is TMDB's.
                _searchError.value = if (t.cause is java.io.IOException) SearchError.Network else SearchError.Server
            } catch (t: Throwable) {
                _searchError.value = SearchError.Unknown
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun add(title: MediaTitle) {
        viewModelScope.launch { repository.add(title) }
    }

    fun setArchived(movie: MovieEntity, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(movie, archived) }
    }

    fun setStatus(movie: MovieEntity, status: WatchStatus) {
        viewModelScope.launch { repository.setStatus(movie, status) }
    }

    fun remove(movie: MovieEntity) {
        viewModelScope.launch { repository.remove(movie) }
    }

    /** Remet en place un titre retiré à l'instant (« Annuler »). */
    fun restore(movie: MovieEntity) {
        viewModelScope.launch { repository.restore(movie) }
    }

    /** Place le titre en tête du classement perso (action rapide d'un appui long). */
    fun moveToTop(movie: MovieEntity) {
        viewModelScope.launch {
            val order = MovieRanking.order(repository.allMovies())
            val index = order.indexOfFirst { it.titleKey == movie.titleKey }
            if (index > 0) repository.applyRanking(MovieRanking.moveTo(order, index, 0))
        }
    }
}

private const val SECONDS_PER_YEAR = 31_556_952L

private fun MovieSort.comparator(): Comparator<MovieEntity> = when (this) {
    MovieSort.RECENTLY_ADDED -> compareByDescending { it.addedAt }
    MovieSort.NAME -> compareBy { it.title.lowercase() }
    MovieSort.RELEASE_DATE -> nullsLast(descending = true) { it.releaseDate ?: it.year?.let { y -> (y - 1970) * SECONDS_PER_YEAR } }
    MovieSort.TMDB_RATING -> nullsLast(descending = true) { it.tmdbRating }
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

class MoviesViewModelFactory(
    private val repository: MovieRepository,
    private val shareLinkService: ShareLinkService? = null,
    private val librarySharer: LibrarySharer? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == MoviesViewModel::class.java)
        return MoviesViewModel(repository, shareLinkService, librarySharer) as T
    }
}
