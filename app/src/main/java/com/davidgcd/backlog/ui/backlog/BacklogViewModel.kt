package com.davidgcd.backlog.ui.backlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.GameJsonCache
import com.davidgcd.backlog.data.local.gameStatus
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.share.ShareItem
import com.davidgcd.backlog.data.share.ShareLinkService
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.model.Ranking
import com.davidgcd.backlog.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

class BacklogViewModel(
    private val repository: BacklogRepository,
    private val shareLinkService: ShareLinkService? = null,
) : ViewModel() {

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

    /** Real per-status counts for the header stat tiles (archived games excluded), never narrowed by filters. */
    val statusCounts: StateFlow<Map<GameStatus, Int>> = backlog
        .map { list ->
            val active = list.filter { !it.isArchived }
            GameStatus.entries.associateWith { status -> active.count { it.gameStatus == status } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameStatus.entries.associateWith { 0 })

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

    /** Everything the share sheet sends: the whole backlog, never narrowed by the current filter. */
    suspend fun gamesToShare(): List<GameEntity> = repository.allGames()

    /**
     * igdb.com links for the shared games. Already-known ones come from the on-device cache; only the
     * missing ones are asked of IGDB (bounded by a timeout). Offline / failing IGDB just means fewer links.
     */
    suspend fun linksToShare(games: List<GameEntity>): Map<Long, String> {
        val ids = games.filter { !it.isArchived }.map { it.igdbId }
        val cached = shareLinkService?.cachedUrls().orEmpty()
        val missing = ids.filter { it !in cached }
        if (missing.isEmpty()) return cached
        return try {
            // null = timed out; keep what we have rather than making the user wait longer.
            val fetched = kotlinx.coroutines.withTimeoutOrNull(10_000) { repository.gameUrls(missing) } ?: return cached
            shareLinkService?.rememberUrls(fetched)
            cached + fetched
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            cached
        }
    }

    /**
     * Publishes the backlog to the share server and returns its public link, or null when there is
     * no server configured or it can't be reached — the caller then shares plain text instead.
     */
    suspend fun publishShareLink(title: String, games: List<GameEntity>, links: Map<Long, String>): String? {
        val service = shareLinkService ?: return null
        val rankOf = Ranking.order(games).filter { it.userRank != null }.mapIndexed { i, g -> g.igdbId to i + 1 }.toMap()
        val items = games.filter { !it.isArchived }.map {
            ShareItem(it.name, it.status, it.coverImageId, links[it.igdbId], rankOf[it.igdbId])
        }
        return try {
            service.publish(title, items)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            AppLogger.network.error("Share link: publish failed, sharing plain text instead", t)
            null
        }
    }

    /** Name shown on the public share page; blank = the user hasn't chosen one yet. */
    suspend fun shareOwnerName(): String = shareLinkService?.ownerName?.first().orEmpty().trim()

    suspend fun setShareOwnerName(name: String) {
        shareLinkService?.setOwnerName(name)
    }

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
    BacklogSort.MY_RANKING -> nullsLastComparator(descending = false) { it.userRank?.toLong() }.thenBy { it.addedAt }
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
