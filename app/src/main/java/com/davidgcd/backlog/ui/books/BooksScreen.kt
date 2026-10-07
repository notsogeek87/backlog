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
                    { shareDetailLink(context, current.title, DetailLink.url(DetailLink.Book(current.bookKey)), context.getString(R.string.share_book_chooser)) },
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
                        OverflowMenuButton(
                            listOf(
                                OverflowAction(stringResource(R.string.action_share_books), onClick = share.start, enabled = !share.sharing),
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

@Composable
private fun BookGrid(
    books: List<BookEntity>,
    filter: BookFilter,
    sort: BookSort,
    searchText: String,
    statusCounts: Map<ReadStatus, Int>,
    onFilterChange: (BookFilter) -> Unit,
    onOpenSheet: () -> Unit,
    onSearchCatalog: () -> Unit,
    onStatusChange: (BookEntity, ReadStatus) -> Unit,
    onLongClick: (BookEntity) -> Unit,
    onClick: (BookEntity) -> Unit,
) {
    val searching = searchText.isNotBlank()
    LazyVerticalGrid(
        // Three covers across on a phone; the grid simply gets wider on a tablet or unfolded screen.
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!searching) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(ReadStatus.TO_READ, ReadStatus.READING, ReadStatus.READ).forEach { status ->
                            StatCard(
                                value = (statusCounts[status] ?: 0).toString(),
                                label = stringResource(status.countLabelRes()),
                                modifier = Modifier.weight(1f),
                                accent = status.tint(),
                                selected = filter.status == status,
                                onClick = { onFilterChange(filter.copy(status = if (filter.status == status) null else status)) },
                            )
                        }
                    }
                    val abandoned = statusCounts[ReadStatus.ABANDONED] ?: 0
                    if (abandoned > 0) {
                        GlassPill(
                            text = stringResource(R.string.books_abandoned_count, abandoned),
                            selected = filter.status == ReadStatus.ABANDONED,
                            onClick = {
                                onFilterChange(filter.copy(status = if (filter.status == ReadStatus.ABANDONED) null else ReadStatus.ABANDONED))
                            },
                        )
                    }
                }
            }
            if (filter.isActive) {
                item(span = { GridItemSpan(maxLineSpan) }) { ActiveFilterChips(filter, onFilterChange) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(
                stringResource(R.string.section_my_books),
                trailing = { SortLabel(stringResource(R.string.sort_active_label, stringResource(sort.labelRes())), onOpenSheet) },
            )
        }
        if (books.isEmpty()) {
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
                        message = stringResource(R.string.books_filtered_empty),
                        actionLabel = stringResource(R.string.filter_reset),
                        onAction = { onFilterChange(BookFilter()) },
                        fillScreen = false,
                    )
                }
            }
        }
        items(books, key = { it.bookKey }) { book ->
            BookCard(
                book = book,
                onClick = { onClick(book) },
                onLongClick = { onLongClick(book) },
                onStatusChange = { onStatusChange(book, it) },
            )
        }
    }
}

/** Cover, title, author and a status badge that opens the status menu — changing it takes two taps, no detail screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(book: BookEntity, onClick: () -> Unit, onLongClick: () -> Unit, onStatusChange: (ReadStatus) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.quick_actions_hint),
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            BookCover(url = BookImage.sized(book.coverUrl, 'M'), title = book.title, modifier = Modifier.fillMaxWidth())
            if (book.isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = stringResource(R.string.book_favorite_label),
                        tint = Glass.Pink,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            book.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Glass.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        book.authorList.firstOrNull()?.let { author ->
            Text(author, style = MaterialTheme.typography.labelMedium, color = Glass.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StatusBadgeMenu(
            label = book.readStatus.label(),
            tint = book.readStatus.tint(),
            choices = ReadStatus.entries.map { option ->
                StatusChoice(option.label(), option.tint(), selected = option == book.readStatus, onSelect = { onStatusChange(option) })
            },
        )
    }
}

@Composable
private fun SearchResults(
    results: List<BookHit>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    fromFallback: Boolean,
    onLoadMore: () -> Unit,
    onAdd: (BookHit) -> Unit,
    onClick: (BookHit) -> Unit,
    extra: LazyListScope.() -> Unit = {},
) {
    // A fresh list state per first hit: new rows can never leave the list anchored below the tab's own results.
    val listState = remember(results.firstOrNull()?.book?.key) { androidx.compose.foundation.lazy.LazyListState() }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(results, key = { it.book.key }) { hit ->
            BookHitItem(hit = hit, onAdd = { onAdd(hit) }, onClick = { onClick(hit) })
        }
        if (results.isNotEmpty() && fromFallback) {
            item {
                Text(stringResource(R.string.books_search_fallback_note), style = MaterialTheme.typography.labelSmall, color = Glass.TextMuted)
            }
        }
        // Results come twenty at a time: more only when the user asks, never a hundred upfront.
        if (hasMore) {
            item {
                if (isLoadingMore) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Glass.Cyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else {
                    GlassButton(text = stringResource(R.string.books_search_more), onClick = onLoadMore, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        extra()
    }
}

/** A search hit: cover, title, "author · year · publisher", and the add / already-added affordance. */
@Composable
internal fun BookHitItem(hit: BookHit, onAdd: () -> Unit, onClick: () -> Unit) {
    val book = hit.book
    val meta = listOfNotNull(
        book.authorLine.ifEmpty { null },
        book.publishedYear?.toString(),
        book.publisher,
    ).joinToString(" · ")
    GameListItem(
        name = book.title,
        coverImageId = null,
        meta = meta,
        cover = { BookCover(url = BookImage.sized(book.coverUrl, 'M'), title = book.title, modifier = Modifier.width(64.dp)) },
        onClick = onClick,
        trailing = {
            if (hit.savedKey != null) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.books_already_saved),
                    tint = Glass.Green,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                AddButton(contentDescription = stringResource(R.string.action_add_to_books), onAdd = onAdd)
            }
        },
    )
}

@Composable
private fun NoResults() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.books_search_none),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            stringResource(R.string.books_search_none_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Glass.TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ActiveFilterChips(filter: BookFilter, onChange: (BookFilter) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        filter.status?.let { RemovableChip(it.label()) { onChange(filter.copy(status = null)) } }
        if (filter.favoritesOnly) RemovableChip(stringResource(R.string.filter_favorites)) { onChange(filter.copy(favoritesOnly = false)) }
        filter.subject?.let { RemovableChip(it) { onChange(filter.copy(subject = null)) } }
    }
}

private fun BookSort.labelRes(): Int = when (this) {
    BookSort.RECENTLY_ADDED -> R.string.sort_recently_added
    BookSort.TITLE -> R.string.sort_title
    BookSort.AUTHOR -> R.string.sort_author
    BookSort.PUBLISHED_YEAR -> R.string.sort_published_year
    BookSort.MY_RATING -> R.string.sort_my_rating
}
