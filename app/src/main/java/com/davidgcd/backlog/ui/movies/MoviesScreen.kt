package com.davidgcd.backlog.ui.movies

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.local.titleKind
import com.davidgcd.backlog.data.local.watchStatus
import com.davidgcd.backlog.model.MediaTitle
import com.davidgcd.backlog.model.TitleKind
import com.davidgcd.backlog.model.WatchStatus
import com.davidgcd.backlog.model.next
import com.davidgcd.backlog.ui.backlog.SearchError
import com.davidgcd.backlog.ui.components.AddButton
import com.davidgcd.backlog.ui.components.FilterSortSheet
import com.davidgcd.backlog.ui.components.GameCover
import com.davidgcd.backlog.ui.components.GameListItem
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.LibraryEmptyState
import com.davidgcd.backlog.ui.components.LibrarySearchField
import com.davidgcd.backlog.ui.components.MediaType
import com.davidgcd.backlog.ui.components.NoResultsMessage
import com.davidgcd.backlog.ui.components.OverflowAction
import com.davidgcd.backlog.ui.components.OverflowMenuButton
import com.davidgcd.backlog.ui.components.QuickAction
import com.davidgcd.backlog.ui.components.QuickActionsSheet
import com.davidgcd.backlog.ui.components.RemovableChip
import com.davidgcd.backlog.ui.components.SearchFeedback
import com.davidgcd.backlog.ui.components.SearchRequest
import com.davidgcd.backlog.ui.components.SearchScope
import com.davidgcd.backlog.ui.components.SearchScopePills
import com.davidgcd.backlog.ui.components.SectionHeader
import com.davidgcd.backlog.ui.components.SheetChoice
import com.davidgcd.backlog.ui.components.SheetSection
import com.davidgcd.backlog.ui.components.SortLabel
import com.davidgcd.backlog.ui.components.StatCard
import com.davidgcd.backlog.ui.components.StatusChoice
import com.davidgcd.backlog.ui.components.SwipeAction
import com.davidgcd.backlog.ui.components.SwipeActionRow
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.components.label
import com.davidgcd.backlog.ui.components.labelRes
import com.davidgcd.backlog.ui.components.rememberShareFlow
import com.davidgcd.backlog.ui.components.shareDetailLink
import com.davidgcd.backlog.ui.components.tint
import com.davidgcd.backlog.ui.search.CrossSearch
import com.davidgcd.backlog.ui.search.CrossSearchState
import com.davidgcd.backlog.ui.search.crossSearchItems
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.DetailLink
import com.davidgcd.backlog.util.MovieShareText
import com.davidgcd.backlog.util.TmdbImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(
    viewModel: MoviesViewModel,
    onMovieClick: (String) -> Unit,
    onOpenTmdbImport: () -> Unit,
    onOpenRanking: () -> Unit = {},
    crossSearch: CrossSearch? = null,
    mediaSwitch: @Composable () -> Unit = {},
    searchRequest: SearchRequest? = null,
    onSearchRequestHandled: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.action_undo)

    var query by remember { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchScope by rememberSaveable { mutableStateOf(SearchScope.LIBRARY) }
    var showSheet by remember { mutableStateOf(false) }
    var quickActionsFor by remember { mutableStateOf<MovieEntity?>(null) }
    val searchFocus = remember { FocusRequester() }

    val visible by viewModel.visible.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val libraryQuery by viewModel.libraryQuery.collectAsState()
    val genres by viewModel.availableGenres.collectAsState()
    val isEmpty by viewModel.isEmpty.collectAsState()
    val statusCounts by viewModel.statusCounts.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()

    val crossState by (crossSearch?.viewModel?.state ?: remember { MutableStateFlow(CrossSearchState()) }).collectAsState()

    fun runSearch(text: String, target: SearchScope) {
        if (target == SearchScope.CATALOG) {
            viewModel.setLibraryQuery("")
            viewModel.search(text)
            crossSearch?.viewModel?.search(text, MediaType.MOVIES)
        } else {
            viewModel.setLibraryQuery(text)
            viewModel.search("")
            crossSearch?.viewModel?.search("", MediaType.MOVIES)
        }
    }

    fun openSearch(target: SearchScope, initial: String = "") {
        searchScope = target
        query = initial
        showSearch = true
        runSearch(initial, target)
    }

    fun closeSearch() {
        showSearch = false
        query = ""
        runSearch("", SearchScope.CATALOG)
        viewModel.setLibraryQuery("")
    }

    BackHandler(enabled = showSearch) { closeSearch() }
    LaunchedEffect(showSearch, searchScope) { if (showSearch) searchFocus.requestFocus() }
    LaunchedEffect(searchRequest) {
        if (searchRequest != null && searchRequest.media == MediaType.MOVIES) {
            openSearch(SearchScope.CATALOG, searchRequest.query.orEmpty())
            onSearchRequestHandled()
        }
    }

    val snack: (String) -> Unit = { text ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }
    val snackWithUndo: (String, () -> Unit) -> Unit = { text, undo ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(message = text, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }
    val onCrossAdded: (String) -> Unit = { name -> snack(context.getString(R.string.snackbar_added, name)) }

    val changeStatus: (MovieEntity, WatchStatus) -> Unit = { movie, status ->
        val previous = movie.watchStatus
        if (previous != status) {
            viewModel.setStatus(movie, status)
            snackWithUndo(context.getString(R.string.snackbar_status_changed, movie.title, context.getString(status.labelRes()))) {
                viewModel.setStatus(movie, previous)
            }
        }
    }
    val toggleArchive: (MovieEntity) -> Unit = { movie ->
        val archive = !movie.isArchived
        viewModel.setArchived(movie, archive)
        snackWithUndo(context.getString(if (archive) R.string.snackbar_archived else R.string.snackbar_unarchived, movie.title)) {
            viewModel.setArchived(movie, !archive)
        }
    }

    val share = rememberShareFlow(
        tabHintRes = R.string.share_scope_tab_movies_hint,
        publishLibraryLink = viewModel::publishLibraryLink,
        shareTab = { ctx ->
            val movies = viewModel.titlesToShare()
            val count = movies.count { !it.isArchived }
            val header = ctx.resources.getQuantityString(R.plurals.share_movies_header, count, count)
            // A public page (posters, best note first) when the server answers; otherwise the plain-text list.
            val publicLink = viewModel.publishShareLink(header, movies)
            val text = if (publicLink != null) {
                "$header\n$publicLink"
            } else {
                MovieShareText.build(
                    movies,
                    MovieShareText.Labels(
                        header = { header },
                        status = { status -> ctx.getString(status.labelRes()) },
                        ranking = ctx.getString(R.string.share_backlog_ranking),
                    ),
                )
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share_movies_chooser)))
        },
    )

    if (showSheet) {
        val resultLabel = when (visible.size) {
            0 -> stringResource(R.string.filter_sheet_show_none)
            1 -> stringResource(R.string.filter_sheet_show_one)
            else -> stringResource(R.string.filter_sheet_show_count, visible.size)
        }
        FilterSortSheet(
            sections = movieSheetSections(sort, filter, genres, viewModel),
            resultLabel = resultLabel,
            onReset = if (filter.isActive) ({ viewModel.setFilter(MovieFilter(kind = filter.kind)) }) else null,
            onDismiss = { showSheet = false },
        )
    }

    quickActionsFor?.let { movie ->
        val current = visible.firstOrNull { it.titleKey == movie.titleKey } ?: movie
        QuickActionsSheet(
            title = current.title,
            statusLabel = stringResource(R.string.quick_actions_status),
            statusChoices = WatchStatus.entries.map { status ->
                StatusChoice(status.label(), status.tint(), selected = current.watchStatus == status, onSelect = { changeStatus(current, status) })
            },
            actions = buildList {
                add(
                    QuickAction(
                        stringResource(if (current.isArchived) R.string.action_unarchive else R.string.action_archive),
                        if (current.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                        { toggleArchive(current) },
                    ),
                )
                if (!current.isArchived) {
                    add(QuickAction(stringResource(R.string.quick_action_top), Icons.Filled.VerticalAlignTop, { viewModel.moveToTop(current) }))
                }
                add(
                    QuickAction(
                        stringResource(R.string.quick_action_share),
                        Icons.Filled.Share,
                        { shareDetailLink(context, current.title, DetailLink.url(DetailLink.Movie(current.titleKey)), context.getString(R.string.share_movie_chooser)) },
                    ),
                )
                add(
                    QuickAction(
                        stringResource(R.string.action_remove),
                        Icons.Filled.Delete,
                        {
                            viewModel.remove(current)
                            snackWithUndo(context.getString(R.string.snackbar_removed, current.title)) { viewModel.restore(current) }
                        },
                        destructive = true,
                    ),
                )
            },
            onDismiss = { quickActionsFor = null },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Glass.Text,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painter = painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.library_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { if (showSearch) closeSearch() else openSearch(if (isEmpty) SearchScope.CATALOG else SearchScope.LIBRARY) }) {
                        Icon(
                            if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(if (showSearch) R.string.action_close_search else R.string.action_search),
                        )
                    }
                    if (!showSearch) {
                        IconButton(onClick = { showSheet = true }) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = stringResource(R.string.filter_sheet_title),
                                tint = if (filter.isActive) Glass.Cyan else Glass.Text,
                            )
                        }
                        OverflowMenuButton(
                            listOf(
                                OverflowAction(stringResource(R.string.action_share_movies), onClick = share.start, enabled = !share.sharing),
                                OverflowAction(stringResource(R.string.action_my_ranking), onClick = onOpenRanking),
                                OverflowAction(stringResource(R.string.movies_empty_import), onClick = onOpenTmdbImport),
                            ),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            mediaSwitch()
            if (showSearch) {
                LibrarySearchField(
                    query = query,
                    onQueryChange = { query = it; runSearch(it, searchScope) },
                    placeholder = stringResource(if (searchScope == SearchScope.LIBRARY) R.string.search_library_placeholder else R.string.movies_search_placeholder),
                    focusRequester = searchFocus,
                    onSearch = { runSearch(query, searchScope) },
                )
                SearchScopePills(searchScope, onScope = { openSearch(it, query) })
            }
            if (showSearch && searchScope == SearchScope.CATALOG) {
                SearchFeedback(
                    isSearching = isSearching,
                    errorText = searchError?.let {
                        stringResource(
                            when (it) {
                                SearchError.Network -> R.string.search_error_network
                                SearchError.Server -> R.string.movies_search_error_tmdb
                                SearchError.Unknown -> R.string.search_error_unknown
                            },
                        )
                    },
                )
                if (!isSearching && searchError == null && query.isNotBlank() && crossState.isEmpty && searchResults.isEmpty()) NoResultsMessage()
                SearchResults(
                    results = searchResults,
                    savedIds = savedIds,
                    onAdd = { title ->
                        viewModel.add(title)
                        snack(context.getString(R.string.snackbar_added, title.title))
                    },
                    onClick = { onMovieClick(it.id) },
                    extra = { if (crossSearch != null && !isSearching) crossSearchItems(crossState, MediaType.MOVIES, crossSearch, onCrossAdded) },
                )
            } else if (isEmpty) {
                LibraryEmptyState(
                    message = stringResource(R.string.movies_empty),
                    actionLabel = stringResource(R.string.movies_empty_cta),
                    onAction = { openSearch(SearchScope.CATALOG) },
                    secondaryLabel = stringResource(R.string.movies_empty_import),
                    onSecondary = onOpenTmdbImport,
                )
            } else {
                MovieGrid(
                    movies = visible,
                    filter = filter,
                    sort = sort,
                    searchText = libraryQuery,
                    statusCounts = statusCounts,
                    recentlyAdded = recentlyAdded,
                    onFilterChange = viewModel::setFilter,
                    onOpenSheet = { showSheet = true },
                    onSearchCatalog = { openSearch(SearchScope.CATALOG, query) },
                    onArchiveToggle = toggleArchive,
                    onNextStatus = { movie -> movie.watchStatus.next()?.let { changeStatus(movie, it) } },
                    onStatusChange = changeStatus,
                    onLongClick = { quickActionsFor = it },
                    onClick = { onMovieClick(it.titleKey) },
                )
            }
        }
    }
}

@Composable
private fun movieSheetSections(sort: MovieSort, filter: MovieFilter, genres: List<String>, viewModel: MoviesViewModel): List<SheetSection> {
    val any = stringResource(R.string.filter_any)
    return buildList {
        add(SheetSection.Choices(stringResource(R.string.filter_sheet_sort), MovieSort.entries.map { option ->
            SheetChoice(stringResource(option.labelRes()), sort == option) { viewModel.setSort(option) }
        }))
        add(SheetSection.Choices(stringResource(R.string.filter_status), listOf<WatchStatus?>(null).plus(WatchStatus.entries).map { status ->
            SheetChoice(status?.label() ?: any, filter.status == status) { viewModel.setFilter(filter.copy(status = status)) }
        }))
        if (genres.isNotEmpty()) {
            add(SheetSection.Choices(stringResource(R.string.filter_genre), listOf<String?>(null).plus(genres).map { genre ->
                SheetChoice(genre ?: any, filter.genre == genre) { viewModel.setFilter(filter.copy(genre = genre)) }
            }))
        }
        add(SheetSection.Toggle(stringResource(R.string.filter_show_archived), filter.showArchived) { viewModel.setFilter(filter.copy(showArchived = it)) })
    }
}

@Composable
private fun MovieGrid(
    movies: List<MovieEntity>,
    filter: MovieFilter,
    sort: MovieSort,
    searchText: String,
    statusCounts: Map<WatchStatus, Int>,
    recentlyAdded: List<MovieEntity>,
    onFilterChange: (MovieFilter) -> Unit,
    onOpenSheet: () -> Unit,
    onSearchCatalog: () -> Unit,
    onArchiveToggle: (MovieEntity) -> Unit,
    onNextStatus: (MovieEntity) -> Unit,
    onStatusChange: (MovieEntity, WatchStatus) -> Unit,
    onLongClick: (MovieEntity) -> Unit,
    onClick: (MovieEntity) -> Unit,
) {
    val searching = searchText.isNotBlank()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!searching) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WatchStatus.entries.forEach { status ->
                        StatCard(
                            value = (statusCounts[status] ?: 0).toString(),
                            label = status.label(),
                            modifier = Modifier.weight(1f),
                            accent = status.tint(),
                            selected = filter.status == status,
                            onClick = { onFilterChange(filter.copy(status = if (filter.status == status) null else status)) },
                        )
                    }
                }
            }
            // Films / Séries chips: the split inside this tab, always visible.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    GlassPill(stringResource(R.string.filter_any), selected = filter.kind == null, onClick = { onFilterChange(filter.copy(kind = null)) })
                    TitleKind.entries.forEach { kind ->
                        GlassPill(kind.label(), selected = filter.kind == kind, onClick = { onFilterChange(filter.copy(kind = kind)) })
                    }
                }
            }
            if (!filter.isActive && filter.kind == null && recentlyAdded.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(stringResource(R.string.section_recent))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            items(recentlyAdded, key = { it.titleKey }) { movie ->
                                GameCover(
                                    imageId = null,
                                    imageUrl = TmdbImage.poster(movie.posterUrl),
                                    width = 112.dp,
                                    contentDescription = movie.title,
                                    modifier = Modifier.clickable { onClick(movie) },
                                )
                            }
                        }
                    }
                }
            }
            if (filter.isActive) {
                item(span = { GridItemSpan(maxLineSpan) }) { ActiveFilterChips(filter, onFilterChange) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(
                stringResource(R.string.section_my_movies),
                trailing = { SortLabel(stringResource(R.string.sort_active_label, stringResource(sort.labelRes())), onOpenSheet) },
            )
        }
        if (movies.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (searching) {
                    LibraryEmptyState(
                        message = stringResource(R.string.search_library_none, searchText),
                        actionLabel = stringResource(R.string.search_in_catalog),
                        onAction = onSearchCatalog,
                        fillScreen = false,
                    )
                } else {
                    LibraryEmptyState(
                        message = stringResource(R.string.movies_filtered_empty),
                        actionLabel = stringResource(R.string.filter_reset),
                        onAction = { onFilterChange(MovieFilter(kind = filter.kind)) },
                        fillScreen = false,
                    )
                }
            }
        }
        items(movies, key = { it.titleKey }) { movie ->
            val next = movie.watchStatus.next()
            SwipeActionRow(
                startAction = next?.let {
                    SwipeAction(stringResource(R.string.swipe_next_status, it.label()), Icons.Filled.SkipNext, it.tint()) { onNextStatus(movie) }
                },
                endAction = SwipeAction(
                    stringResource(if (movie.isArchived) R.string.action_unarchive else R.string.swipe_archive),
                    if (movie.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                    Glass.Amber,
                ) { onArchiveToggle(movie) },
            ) {
                MovieListItem(
                    movie = movie,
                    onClick = { onClick(movie) },
                    statusChoices = WatchStatus.entries.map { status ->
                        StatusChoice(status.label(), status.tint(), selected = status == movie.watchStatus, onSelect = { onStatusChange(movie, status) })
                    },
                    onLongClick = { onLongClick(movie) },
                    trailing = {
                        IconButton(onClick = { onArchiveToggle(movie) }) {
                            Icon(
                                if (movie.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                contentDescription = stringResource(if (movie.isArchived) R.string.action_unarchive else R.string.action_archive),
                                tint = Glass.TextMuted,
                            )
                        }
                    },
                )
            }
        }
    }
}

/** One saved title as a list row. */
@Composable
fun MovieListItem(
    movie: MovieEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    statusChoices: List<StatusChoice>? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val meta = listOfNotNull(
        movie.titleKind.label(),
        movie.year?.toString(),
        movie.runtimeMinutes?.let { stringResource(R.string.movie_runtime_minutes, it) },
        if (movie.isArchived) stringResource(R.string.label_archived) else null,
    ).joinToString(" · ")
    GameListItem(
        name = movie.title,
        coverImageId = null,
        coverUrl = TmdbImage.poster(movie.posterUrl, width = 185),
        modifier = modifier,
        meta = meta,
        rating = movie.tmdbRating?.let { it * 10 },
        ratingText = movie.tmdbRating?.let { "%.1f".format(java.util.Locale.FRENCH, it) },
        dimmed = movie.isArchived,
        statusLabel = movie.watchStatus.label(),
        statusTint = movie.watchStatus.tint(),
        statusChoices = statusChoices,
        userRatingLabel = movie.userRating?.let { stringResource(R.string.movie_my_rating_badge, it) },
        onLongClick = onLongClick,
        onLongClickLabel = if (onLongClick != null) stringResource(R.string.quick_actions_hint) else null,
        onClick = onClick,
        trailing = trailing,
    )
}

@Composable
private fun ActiveFilterChips(filter: MovieFilter, onChange: (MovieFilter) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        filter.status?.let { RemovableChip(it.label()) { onChange(filter.copy(status = null)) } }
        filter.genre?.let { RemovableChip(it) { onChange(filter.copy(genre = null)) } }
        if (filter.showArchived) RemovableChip(stringResource(R.string.filter_show_archived)) { onChange(filter.copy(showArchived = false)) }
    }
}

private fun MovieSort.labelRes(): Int = when (this) {
    MovieSort.RECENTLY_ADDED -> R.string.sort_recently_added
    MovieSort.NAME -> R.string.sort_name
    MovieSort.RELEASE_DATE -> R.string.sort_release_date
    MovieSort.TMDB_RATING -> R.string.sort_tmdb_rating
    MovieSort.MY_RATING -> R.string.sort_my_rating
    MovieSort.MY_RANKING -> R.string.sort_my_ranking
}

@Composable
private fun SearchResults(
    results: List<MediaTitle>,
    savedIds: Set<String>,
    onAdd: (MediaTitle) -> Unit,
    onClick: (MediaTitle) -> Unit,
    extra: LazyListScope.() -> Unit = {},
) {
    // A fresh list state per first hit: new rows can never leave the list anchored below the tab's own results.
    val listState = remember(results.firstOrNull()?.id) { androidx.compose.foundation.lazy.LazyListState() }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(results, key = { it.id }) { title ->
            MediaTitleListItem(title = title, saved = title.id in savedIds, onAdd = { onAdd(title) }, onClick = { onClick(title) })
        }
        extra()
    }
}

/** A not-yet-saved title (search hit, chart entry) with the add / already-saved affordance. */
@Composable
fun MediaTitleListItem(title: MediaTitle, saved: Boolean, onAdd: () -> Unit, onClick: () -> Unit) {
    val meta = listOfNotNull(
        title.kind.label(),
        title.year?.toString(),
        title.cast?.takeIf { title.rating == null },
    ).joinToString(" · ")
    GameListItem(
        name = title.title,
        coverImageId = null,
        coverUrl = TmdbImage.poster(title.posterUrl, width = 185),
        meta = meta,
        rating = title.rating?.let { it * 10 },
        ratingText = title.rating?.let { "%.1f".format(java.util.Locale.FRENCH, it) },
        onClick = onClick,
        trailing = {
            if (saved) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.movies_already_saved),
                    tint = Glass.Green,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                AddButton(contentDescription = stringResource(R.string.action_add_to_movies), onAdd = onAdd)
            }
        },
    )
}
