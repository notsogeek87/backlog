package com.davidgcd.backlog.ui.books

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.authorList
import com.davidgcd.backlog.data.local.readStatus
import com.davidgcd.backlog.data.repository.BookAddResult
import com.davidgcd.backlog.model.ReadStatus
import com.davidgcd.backlog.ui.backlog.SearchError
import com.davidgcd.backlog.ui.components.AddButton
import com.davidgcd.backlog.ui.components.BookCover
import com.davidgcd.backlog.ui.components.FilterSortSheet
import com.davidgcd.backlog.ui.components.GameListItem
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.LibraryEmptyState
import com.davidgcd.backlog.ui.components.LibrarySearchField
import com.davidgcd.backlog.ui.components.MediaType
import com.davidgcd.backlog.ui.components.OverflowAction
import com.davidgcd.backlog.ui.components.OverflowMenuButton
import com.davidgcd.backlog.ui.components.QuickAction
import com.davidgcd.backlog.ui.components.QuickActionsSheet
import com.davidgcd.backlog.ui.components.RemovableChip
import com.davidgcd.backlog.ui.components.SearchFeedback
import com.davidgcd.backlog.ui.components.SearchCarry
import com.davidgcd.backlog.ui.components.SearchRequest
import com.davidgcd.backlog.ui.components.SearchScope
import com.davidgcd.backlog.ui.components.SearchScopePills
import com.davidgcd.backlog.ui.components.SectionHeader
import com.davidgcd.backlog.ui.components.SheetChoice
import com.davidgcd.backlog.ui.components.SheetSection
import com.davidgcd.backlog.ui.components.SortLabel
import com.davidgcd.backlog.ui.components.StatCard
import com.davidgcd.backlog.ui.components.StatusBadgeMenu
import com.davidgcd.backlog.ui.components.StatusChoice
import com.davidgcd.backlog.ui.components.countLabelRes
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.components.label
import com.davidgcd.backlog.ui.components.labelRes
import com.davidgcd.backlog.ui.components.NoResultsMessage
import com.davidgcd.backlog.ui.components.rememberShareFlow
import com.davidgcd.backlog.ui.components.shareDetailLink
import com.davidgcd.backlog.ui.components.tint
import com.davidgcd.backlog.ui.search.CrossSearch
import com.davidgcd.backlog.ui.search.CrossSearchState
import com.davidgcd.backlog.ui.search.crossSearchItems
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.BookImage
import com.davidgcd.backlog.util.BookShareText
import com.davidgcd.backlog.util.DetailLink
import com.davidgcd.backlog.util.DetailPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    viewModel: BooksViewModel,
    onBookClick: (String) -> Unit,
    crossSearch: CrossSearch? = null,
    mediaSwitch: @Composable () -> Unit = {},
    searchRequest: SearchRequest? = null,
    onSearchRequestHandled: () -> Unit = {},
    /** Recherche à reprendre quand on change de catégorie depuis la barre Jeux / Films & séries / Livres. */
    searchCarry: SearchCarry? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val openLabel = stringResource(R.string.action_open)
    val undoLabel = stringResource(R.string.action_undo)

    var query by remember { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchScope by rememberSaveable { mutableStateOf(SearchScope.LIBRARY) }
    var showSheet by remember { mutableStateOf(false) }
    var quickActionsFor by remember { mutableStateOf<BookEntity?>(null) }
    val searchFocus = remember { FocusRequester() }
    var focusTick by remember { mutableStateOf(0) }

    val visible by viewModel.visible.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val libraryQuery by viewModel.libraryQuery.collectAsState()
    val subjects by viewModel.availableSubjects.collectAsState()
    val isEmpty by viewModel.isEmpty.collectAsState()
    val statusCounts by viewModel.statusCounts.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val fromFallback by viewModel.fromFallback.collectAsState()
    val searchError by viewModel.searchError.collectAsState()

    val crossState by (crossSearch?.viewModel?.state ?: remember { MutableStateFlow(CrossSearchState()) }).collectAsState()

    fun runSearch(text: String, target: SearchScope) {
        if (target == SearchScope.CATALOG) {
            viewModel.setLibraryQuery("")
            viewModel.search(text)
            crossSearch?.viewModel?.search(text, MediaType.BOOKS)
        } else {
            viewModel.setLibraryQuery(text)
            viewModel.search("")
            crossSearch?.viewModel?.search("", MediaType.BOOKS)
        }
    }

    fun openSearch(target: SearchScope, initial: String = "", focus: Boolean = true) {
        if (focus) focusTick++
        searchScope = target
        query = initial
        showSearch = true
        runSearch(initial, target)
        searchCarry?.let { it.scope = target; it.query = initial }
    }

    fun closeSearch() {
        showSearch = false
        query = ""
        searchCarry?.clear()
        runSearch("", SearchScope.CATALOG)
        viewModel.setLibraryQuery("")
    }

    BackHandler(enabled = showSearch) { closeSearch() }
    // Focus seulement à l'ouverture voulue de la recherche, pas au retour d'un résultat (sinon le clavier se rouvre).
    LaunchedEffect(focusTick) { if (focusTick > 0 && showSearch) searchFocus.requestFocus() }
    LaunchedEffect(Unit) {
        val carried = searchCarry?.scope
        if (carried != null && searchRequest == null) openSearch(carried, searchCarry?.query.orEmpty(), focus = false)
    }
    LaunchedEffect(searchRequest) {
        if (searchRequest != null && searchRequest.media == MediaType.BOOKS) {
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

    val changeStatus: (BookEntity, ReadStatus) -> Unit = { book, status ->
        val previous = book.readStatus
        if (previous != status) {
            viewModel.setStatus(book, status)
            snackWithUndo(context.getString(R.string.snackbar_status_changed, book.title, context.getString(status.labelRes()))) {
                viewModel.setStatus(book, previous)
            }
        }
    }

    val share = rememberShareFlow(
        tabHintRes = R.string.share_scope_tab_books_hint,
        publishLibraryLink = viewModel::publishLibraryLink,
        shareTab = { ctx ->
            val books = viewModel.booksToShare()
            val header = ctx.resources.getQuantityString(R.plurals.share_books_header, books.size, books.size)
            // A public page (covers, best note first) when the server answers; otherwise the plain-text list.
            val publicLink = viewModel.publishShareLink(header, books)
            val text = if (publicLink != null) {
                "$header\n$publicLink"
            } else {
                BookShareText.build(
                    books,
                    BookShareText.Labels(
                        header = { header },
                        status = { status -> ctx.getString(status.labelRes()) },
                        ratings = ctx.getString(R.string.share_books_ratings),
                    ),
                )
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share_books_chooser)))
        },
    )

    if (showSheet) {
        val resultLabel = when (visible.size) {
            0 -> stringResource(R.string.filter_sheet_show_none)
            1 -> stringResource(R.string.filter_sheet_show_one)
            else -> stringResource(R.string.filter_sheet_show_count, visible.size)
        }
        FilterSortSheet(
            sections = bookSheetSections(sort, filter, subjects, viewModel),
            resultLabel = resultLabel,
            onReset = if (filter.isActive) ({ viewModel.setFilter(BookFilter()) }) else null,
            onDismiss = { showSheet = false },
        )
    }

    quickActionsFor?.let { book ->
        val current = visible.firstOrNull { it.bookKey == book.bookKey } ?: book
        QuickActionsSheet(
            title = current.title,
            statusLabel = stringResource(R.string.quick_actions_status),
            statusChoices = ReadStatus.entries.map { status ->
                StatusChoice(status.label(), status.tint(), selected = current.readStatus == status, onSelect = { changeStatus(current, status) })
            },
            actions = listOf(
                QuickAction(
                    stringResource(if (current.isFavorite) R.string.quick_action_unfavorite else R.string.quick_action_favorite),
                    if (current.isFavorite) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite,
                    { viewModel.setFavorite(current, !current.isFavorite) },
                ),
                QuickAction(
                    stringResource(R.string.quick_action_share),
                    Icons.Filled.Share,
                    { shareDetailLink(context, current.title, DetailLink.url(DetailLink.Book(current.bookKey), DetailPreview.of(current)), context.getString(R.string.share_book_chooser)) },
                ),
                QuickAction(
                    stringResource(R.string.action_remove),
                    Icons.Filled.Delete,
                    {
                        viewModel.remove(current)
                        snackWithUndo(context.getString(R.string.snackbar_removed, current.title)) { viewModel.restore(current) }
                    },
                    destructive = true,
                ),
            ),
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
                        IconButton(onClick = share.start, enabled = !share.sharing) {
                            if (share.sharing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Glass.Text)
                            } else {
                                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share_books))
                            }
                        }
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
                    onQueryChange = { query = it; searchCarry?.let { c -> c.scope = searchScope; c.query = it }; runSearch(it, searchScope) },
                    placeholder = stringResource(if (searchScope == SearchScope.LIBRARY) R.string.search_library_placeholder else R.string.books_search_placeholder),
                    focusRequester = searchFocus,
                    onSearch = { runSearch(query, searchScope) },
                )
                SearchScopePills(searchScope, onScope = { openSearch(it, query) })
            }
            if (showSearch && searchScope == SearchScope.CATALOG) {
                SearchFeedback(
                    isSearching = isSearching,
                    // Never the technical cause: only what the user can do about it.
                    errorText = searchError?.let {
                        stringResource(
                            when (it) {
                                SearchError.Network -> R.string.books_search_error_network
                                SearchError.Server -> R.string.books_search_error_server
                                SearchError.Unknown -> R.string.books_search_error_unknown
                            },
                        )
                    },
                )
                if (!isSearching && searchError == null && query.isNotBlank() && crossState.isEmpty && results.isEmpty()) {
                    NoResults()
                }
                SearchResults(
                    results = results,
                    hasMore = hasMore,
                    isLoadingMore = isLoadingMore,
                    fromFallback = fromFallback,
                    onLoadMore = viewModel::loadMore,
                    onAdd = { hit ->
                        viewModel.add(hit.book) { result ->
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                when (result) {
                                    is BookAddResult.Added -> snackbarHostState.showSnackbar(
                                        context.getString(R.string.snackbar_book_added, hit.book.title),
                                        duration = SnackbarDuration.Short,
                                    )
                                    is BookAddResult.Duplicate -> {
                                        val outcome = snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.book_already_in_backlog),
                                            actionLabel = openLabel,
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (outcome == SnackbarResult.ActionPerformed) onBookClick(result.existingKey)
                                    }
                                }
                            }
                        }
                    },
                    onClick = { hit -> onBookClick(hit.savedKey ?: hit.book.key) },
                    extra = { if (crossSearch != null && !isSearching) crossSearchItems(crossState, MediaType.BOOKS, crossSearch, onCrossAdded) },
                )
            } else if (isEmpty) {
                LibraryEmptyState(
                    message = stringResource(R.string.books_empty),
                    actionLabel = stringResource(R.string.books_empty_cta),
                    onAction = { openSearch(SearchScope.CATALOG) },
                )
            } else {
                BookGrid(
                    books = visible,
                    filter = filter,
                    sort = sort,
                    searchText = libraryQuery,
                    statusCounts = statusCounts,
                    onFilterChange = viewModel::setFilter,
                    onOpenSheet = { showSheet = true },
                    onSearchCatalog = { openSearch(SearchScope.CATALOG, query) },
                    onStatusChange = changeStatus,
                    onLongClick = { quickActionsFor = it },
                    onClick = { onBookClick(it.bookKey) },
                )
            }
        }
    }
}

@Composable
private fun bookSheetSections(sort: BookSort, filter: BookFilter, subjects: List<String>, viewModel: BooksViewModel): List<SheetSection> {
    val any = stringResource(R.string.filter_any)
    return buildList {
        add(SheetSection.Choices(stringResource(R.string.filter_sheet_sort), BookSort.entries.map { option ->
            SheetChoice(stringResource(option.labelRes()), sort == option) { viewModel.setSort(option) }
        }))
        add(SheetSection.Choices(stringResource(R.string.filter_status), listOf<ReadStatus?>(null).plus(ReadStatus.entries).map { status ->
            SheetChoice(status?.label() ?: any, filter.status == status) { viewModel.setFilter(filter.copy(status = status)) }
        }))
        if (subjects.isNotEmpty()) {
            add(SheetSection.Choices(stringResource(R.string.filter_genre), listOf<String?>(null).plus(subjects).map { subject ->
                SheetChoice(subject ?: any, filter.subject == subject) { viewModel.setFilter(filter.copy(subject = subject)) }
            }))
        }
        add(SheetSection.Toggle(stringResource(R.string.filter_favorites), filter.favoritesOnly) { viewModel.setFilter(filter.copy(favoritesOnly = it)) })
    }
}
