package com.davidgcd.backlog.ui.backlog

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.Image
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.GameJsonCache
import com.davidgcd.backlog.data.local.gameStatus
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.model.next
import com.davidgcd.backlog.model.suggestedFor
import com.davidgcd.backlog.ui.components.AddButton
import com.davidgcd.backlog.ui.components.AddChoice
import com.davidgcd.backlog.ui.components.FilterSortSheet
import com.davidgcd.backlog.ui.components.GameCover
import com.davidgcd.backlog.ui.components.GameListItem
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
import com.davidgcd.backlog.util.BacklogShareText
import com.davidgcd.backlog.util.DetailLink
import com.davidgcd.backlog.util.FrenchLabels
import com.davidgcd.backlog.util.ReleaseDateFormatting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogScreen(
    viewModel: BacklogViewModel,
    onGameClick: (Long) -> Unit,
    onOpenRanking: () -> Unit,
    crossSearch: CrossSearch? = null,
    /** Jeux / Films & séries / Livres, rendu sous la barre du haut quand l'hôte (la Bibliothèque) en fournit un. */
    mediaSwitch: @Composable () -> Unit = {},
    /** Recherche demandée de l'extérieur (raccourci, texte partagé) ; consommée une fois. */
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
    var quickActionsFor by remember { mutableStateOf<GameEntity?>(null) }
    val searchFocus = remember { FocusRequester() }

    val visibleBacklog by viewModel.visibleBacklog.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val libraryQuery by viewModel.libraryQuery.collectAsState()
    val availableGenres by viewModel.availableGenres.collectAsState()
    val availablePlatforms by viewModel.availablePlatforms.collectAsState()
    val isBacklogEmpty by viewModel.isBacklogEmpty.collectAsState()
    val statusCounts by viewModel.statusCounts.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val backlogIds by viewModel.backlogIds.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()

    val crossState by (crossSearch?.viewModel?.state ?: remember { MutableStateFlow(CrossSearchState()) }).collectAsState()

    fun runSearch(text: String, target: SearchScope) {
        if (target == SearchScope.CATALOG) {
            viewModel.setLibraryQuery("")
            viewModel.search(text)
            crossSearch?.viewModel?.search(text, MediaType.GAMES)
        } else {
            viewModel.setLibraryQuery(text)
            viewModel.search("")
            crossSearch?.viewModel?.search("", MediaType.GAMES)
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
    LaunchedEffect(showSearch, searchScope) {
        if (showSearch) searchFocus.requestFocus()
    }
    LaunchedEffect(searchRequest) {
        if (searchRequest != null && searchRequest.media == MediaType.GAMES) {
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

    val changeStatus: (GameEntity, GameStatus) -> Unit = { game, status ->
        val previous = game.gameStatus
        if (previous != status) {
            viewModel.setStatus(game, status)
            snackWithUndo(context.getString(R.string.snackbar_status_changed, game.name, context.getString(status.labelRes()))) {
                viewModel.setStatus(game, previous)
            }
        }
    }
    val toggleArchive: (GameEntity) -> Unit = { game ->
        val archive = !game.isArchived
        viewModel.setArchived(game, archive)
        snackWithUndo(context.getString(if (archive) R.string.snackbar_archived else R.string.snackbar_unarchived, game.name)) {
            viewModel.setArchived(game, !archive)
        }
    }

    val share = rememberShareFlow(
        tabHintRes = R.string.share_scope_tab_games_hint,
        publishLibraryLink = viewModel::publishLibraryLink,
        ownerName = viewModel::shareOwnerName,
        saveOwnerName = viewModel::setShareOwnerName,
        shareTab = { ctx ->
            val games = viewModel.gamesToShare()
            val links = viewModel.linksToShare(games)
            val header = ctx.resources.getQuantityString(R.plurals.share_backlog_header, games.count { !it.isArchived }, games.count { !it.isArchived })
            // A public page when the server answers; otherwise the plain-text list, still with IGDB links.
            val publicLink = viewModel.publishShareLink(header, games, links)
            val text = if (publicLink != null) {
                "$header\n$publicLink"
            } else {
                BacklogShareText.build(
                    games,
                    BacklogShareText.Labels(
                        header = { header },
                        status = { status -> ctx.getString(status.labelRes()) },
                        ranking = ctx.getString(R.string.share_backlog_ranking),
                    ),
                    links = links,
                )
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share_backlog_chooser)))
        },
    )

    if (showSheet) {
        val resultLabel = when (visibleBacklog.size) {
            0 -> stringResource(R.string.filter_sheet_show_none)
            1 -> stringResource(R.string.filter_sheet_show_one)
            else -> stringResource(R.string.filter_sheet_show_count, visibleBacklog.size)
        }
        FilterSortSheet(
            sections = gameSheetSections(sort, filter, availableGenres, availablePlatforms, viewModel),
            resultLabel = resultLabel,
            onReset = if (filter.isActive || filter.scope != BacklogScope.ALL) ({ viewModel.setFilter(BacklogFilter()) }) else null,
            onDismiss = { showSheet = false },
        )
    }

    quickActionsFor?.let { game ->
        // Look the row up again so the sheet follows live changes (status, archived).
        val current = visibleBacklog.firstOrNull { it.igdbId == game.igdbId } ?: game
        QuickActionsSheet(
            title = current.name,
            statusLabel = stringResource(R.string.quick_actions_status),
            statusChoices = GameStatus.entries.map { status ->
                StatusChoice(status.label(), status.tint(), selected = current.gameStatus == status, onSelect = { changeStatus(current, status) })
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
                        { shareDetailLink(context, current.name, DetailLink.url(DetailLink.Game(current.igdbId)), context.getString(R.string.share_game_chooser)) },
                    ),
                )
                add(
                    QuickAction(
                        stringResource(R.string.action_remove),
                        Icons.Filled.Delete,
                        {
                            viewModel.remove(current)
                            snackWithUndo(context.getString(R.string.snackbar_removed, current.name)) { viewModel.restore(current) }
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
                        Text(
                            stringResource(R.string.library_title),
                            fontWeight = FontWeight.Bold,
                            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { if (showSearch) closeSearch() else openSearch(if (isBacklogEmpty) SearchScope.CATALOG else SearchScope.LIBRARY) }) {
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
                                tint = if (filter.isActive || filter.scope != BacklogScope.ALL) Glass.Cyan else Glass.Text,
                            )
                        }
                        IconButton(onClick = share.start, enabled = !share.sharing) {
                            if (share.sharing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Glass.Text)
                            } else {
                                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share_backlog))
                            }
                        }
                        OverflowMenuButton(
                            listOf(OverflowAction(stringResource(R.string.action_my_ranking), onClick = onOpenRanking)),
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
                    placeholder = stringResource(if (searchScope == SearchScope.LIBRARY) R.string.search_library_placeholder else R.string.search_placeholder),
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
                                SearchError.Server -> R.string.search_error_server
                                SearchError.Unknown -> R.string.search_error_unknown
                            },
                        )
                    },
                )
                if (!isSearching && searchError == null && query.isNotBlank() && crossState.isEmpty && searchResults.isEmpty()) NoResultsMessage()
                SearchResultsList(
                    results = searchResults,
                    backlogIds = backlogIds,
                    onAdd = { game, status ->
                        val used = status ?: GameStatus.suggestedFor(game.firstReleaseDate)
                        viewModel.addToBacklog(game, status)
                        snack(
                            context.getString(
                                if (used == GameStatus.WISHLIST) R.string.snackbar_added_wishlist else R.string.snackbar_added,
                                game.name,
                            ),
                        )
                    },
                    onClick = { onGameClick(it.id) },
                    extra = { if (crossSearch != null && !isSearching) crossSearchItems(crossState, MediaType.GAMES, crossSearch, onCrossAdded) },
                )
            } else if (isBacklogEmpty) {
                LibraryEmptyState(
                    message = stringResource(R.string.backlog_empty),
                    actionLabel = stringResource(R.string.backlog_empty_cta),
                    onAction = { openSearch(SearchScope.CATALOG) },
                )
            } else {
                BacklogGrid(
                    games = visibleBacklog,
                    filter = filter,
                    sort = sort,
                    searchText = libraryQuery,
                    statusCounts = statusCounts,
                    recentlyAdded = recentlyAdded,
                    onFilterChange = viewModel::setFilter,
                    onOpenSheet = { showSheet = true },
                    onSearchCatalog = { openSearch(SearchScope.CATALOG, query) },
                    onArchiveToggle = toggleArchive,
                    onNextStatus = { game -> game.gameStatus.next()?.let { changeStatus(game, it) } },
                    onStatusChange = changeStatus,
                    onLongClick = { quickActionsFor = it },
                    onClick = { onGameClick(it.igdbId) },
                )
            }
        }
    }
}

/** Les sections de la feuille « Filtrer et trier » d'un jeu : tri, affichage, statut, genre, plateforme, archivés. */
@Composable
private fun gameSheetSections(
    sort: BacklogSort,
    filter: BacklogFilter,
    genres: List<String>,
    platforms: List<String>,
    viewModel: BacklogViewModel,
): List<SheetSection> {
    val any = stringResource(R.string.filter_any)
    return buildList {
        add(SheetSection.Choices(stringResource(R.string.filter_sheet_sort), BacklogSort.entries.map { option ->
            SheetChoice(option.label(), sort == option) { viewModel.setSort(option) }
        }))
        add(SheetSection.Choices(stringResource(R.string.filter_scope_title), listOf(
            BacklogScope.ALL to R.string.scope_all,
            BacklogScope.WISHLIST to R.string.scope_wishlist,
            BacklogScope.OWNED to R.string.scope_owned,
        ).map { (value, label) -> SheetChoice(stringResource(label), filter.scope == value) { viewModel.setFilter(filter.copy(scope = value)) } }))
        add(SheetSection.Choices(stringResource(R.string.filter_status), listOf<GameStatus?>(null).plus(GameStatus.entries).map { status ->
            SheetChoice(status?.label() ?: any, filter.status == status) { viewModel.setFilter(filter.copy(status = status)) }
        }))
        if (genres.isNotEmpty()) {
            add(SheetSection.Choices(stringResource(R.string.filter_genre), listOf<String?>(null).plus(genres).map { genre ->
                SheetChoice(genre?.let(FrenchLabels::genre) ?: any, filter.genre == genre) { viewModel.setFilter(filter.copy(genre = genre)) }
            }))
        }
        if (platforms.isNotEmpty()) {
            add(SheetSection.Choices(stringResource(R.string.filter_platform), listOf<String?>(null).plus(platforms).map { platform ->
                SheetChoice(platform?.let(FrenchLabels::platform) ?: any, filter.platform == platform) { viewModel.setFilter(filter.copy(platform = platform)) }
            }))
        }
        add(SheetSection.Toggle(stringResource(R.string.filter_show_archived), filter.showArchived) { viewModel.setFilter(filter.copy(showArchived = it)) })
    }
}

/**
 * Home + list in one adaptive grid: 1 column on phones, several on tablets/desktop.
 * Header blocks (stats, recent carousel, active filters) span the full width.
 */
@Composable
private fun BacklogGrid(
    games: List<GameEntity>,
    filter: BacklogFilter,
    sort: BacklogSort,
    searchText: String,
    statusCounts: Map<GameStatus, Int>,
    recentlyAdded: List<GameEntity>,
    onFilterChange: (BacklogFilter) -> Unit,
    onOpenSheet: () -> Unit,
    onSearchCatalog: () -> Unit,
    onArchiveToggle: (GameEntity) -> Unit,
    onNextStatus: (GameEntity) -> Unit,
    onStatusChange: (GameEntity, GameStatus) -> Unit,
    onLongClick: (GameEntity) -> Unit,
    onClick: (GameEntity) -> Unit,
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
            // The status tiles are the status filter: tap one to narrow the list, tap it again to clear.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GameStatus.entries.forEach { status ->
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
            if (!filter.isActive && filter.scope == BacklogScope.ALL && recentlyAdded.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(stringResource(R.string.section_recent))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            items(recentlyAdded, key = { it.igdbId }) { game ->
                                GameCover(
                                    imageId = game.coverImageId,
                                    width = 112.dp,
                                    contentDescription = game.name,
                                    modifier = Modifier.clickable { onClick(game) },
                                )
                            }
                        }
                    }
                }
            }
            if (filter.isActive || filter.scope != BacklogScope.ALL) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ActiveFilterChips(filter = filter, onChange = onFilterChange)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(
                stringResource(R.string.section_my_games),
                trailing = { SortLabel(stringResource(R.string.sort_active_label, sort.label()), onOpenSheet) },
            )
        }
        if (games.isEmpty()) {
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
                        message = stringResource(R.string.backlog_filtered_empty),
                        actionLabel = stringResource(R.string.filter_reset),
                        onAction = { onFilterChange(BacklogFilter()) },
                        fillScreen = false,
                    )
                }
            }
        }
        items(games, key = { it.igdbId }) { game ->
            val supporting = listOfNotNull(
                ReleaseDateFormatting.format(game.firstReleaseDate),
                if (game.isArchived) stringResource(R.string.label_archived) else null,
            ).joinToString(" · ")
            val next = game.gameStatus.next()
            SwipeActionRow(
                startAction = next?.let {
                    SwipeAction(stringResource(R.string.swipe_next_status, it.label()), Icons.Filled.SkipNext, it.tint()) { onNextStatus(game) }
                },
                endAction = SwipeAction(
                    stringResource(if (game.isArchived) R.string.action_unarchive else R.string.swipe_archive),
                    if (game.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                    Glass.Amber,
                ) { onArchiveToggle(game) },
            ) {
                GameListItem(
                    name = game.name,
                    coverImageId = game.coverImageId,
                    platforms = GameJsonCache.platformNames(game).map(FrenchLabels::platform),
                    meta = supporting,
                    rating = game.totalRating,
                    dimmed = game.isArchived,
                    statusLabel = game.gameStatus.label(),
                    statusTint = game.gameStatus.tint(),
                    statusChoices = GameStatus.entries.map { status ->
                        StatusChoice(status.label(), status.tint(), selected = status == game.gameStatus, onSelect = { onStatusChange(game, status) })
                    },
                    userRatingLabel = game.userRating?.let { "★ $it/10" },
                    onLongClick = { onLongClick(game) },
                    onLongClickLabel = stringResource(R.string.quick_actions_hint),
                    onClick = { onClick(game) },
                    trailing = {
                        val label = stringResource(if (game.isArchived) R.string.action_unarchive else R.string.action_archive)
                        IconButton(onClick = { onArchiveToggle(game) }) {
                            Icon(
                                if (game.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                contentDescription = label,
                                tint = Glass.TextMuted,
                            )
                        }
                    },
                )
            }
        }
    }
}

/** Active filters stay visible (and one tap removable) after the sheet closes. */
@Composable
private fun ActiveFilterChips(filter: BacklogFilter, onChange: (BacklogFilter) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (filter.scope) {
            BacklogScope.WISHLIST -> RemovableChip(stringResource(R.string.scope_wishlist)) { onChange(filter.copy(scope = BacklogScope.ALL)) }
            BacklogScope.OWNED -> RemovableChip(stringResource(R.string.scope_owned)) { onChange(filter.copy(scope = BacklogScope.ALL)) }
            BacklogScope.ALL -> Unit
        }
        filter.status?.let { RemovableChip(it.label()) { onChange(filter.copy(status = null)) } }
        filter.genre?.let { RemovableChip(FrenchLabels.genre(it)) { onChange(filter.copy(genre = null)) } }
        filter.platform?.let { RemovableChip(FrenchLabels.platform(it)) { onChange(filter.copy(platform = null)) } }
        if (filter.showArchived) {
            RemovableChip(stringResource(R.string.filter_show_archived)) { onChange(filter.copy(showArchived = false)) }
        }
    }
}

@Composable
private fun BacklogSort.label(): String = stringResource(
    when (this) {
        BacklogSort.RECENTLY_ADDED -> R.string.sort_recently_added
        BacklogSort.NAME -> R.string.sort_name
        BacklogSort.RELEASE_DATE -> R.string.sort_release_date
        BacklogSort.RATING -> R.string.sort_rating
        BacklogSort.MY_RANKING -> R.string.sort_my_ranking
    },
)

@Composable
private fun SearchResultsList(
    results: List<Game>,
    backlogIds: Set<Long>,
    onAdd: (Game, GameStatus?) -> Unit,
    onClick: (Game) -> Unit,
    extra: LazyListScope.() -> Unit = {},
) {
    // A fresh list state per first hit: new rows can never leave the list anchored below the tab's own results.
    val listState = remember(results.firstOrNull()?.id) { androidx.compose.foundation.lazy.LazyListState() }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(results, key = { it.id }) { game ->
            GameListItem(
                name = game.name,
                coverImageId = game.cover?.imageId,
                platforms = game.platforms?.map { FrenchLabels.platform(it.name) } ?: emptyList(),
                meta = ReleaseDateFormatting.format(game.firstReleaseDate),
                onClick = { onClick(game) },
                trailing = {
                    if (backlogIds.contains(game.id)) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.discover_already_in_backlog),
                            tint = Glass.Green,
                            modifier = Modifier.padding(12.dp),
                        )
                    } else {
                        AddButton(
                            contentDescription = stringResource(R.string.action_add_to_backlog),
                            onAdd = { onAdd(game, null) },
                            choices = GameStatus.entries.map { status -> AddChoice(status.label()) { onAdd(game, status) } },
                        )
                    }
                },
            )
        }
        extra()
    }
}
