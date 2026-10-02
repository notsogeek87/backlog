package com.davidgcd.backlog.ui.books

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.authorList
import com.davidgcd.backlog.data.local.readStatus
import com.davidgcd.backlog.data.repository.BookAddResult
import com.davidgcd.backlog.model.ReadStatus
import com.davidgcd.backlog.ui.backlog.SearchError
import com.davidgcd.backlog.ui.components.BookCover
import com.davidgcd.backlog.ui.components.GameListItem
import com.davidgcd.backlog.ui.components.GlassBadge
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.components.ShareScopeDialog
import com.davidgcd.backlog.ui.components.labelRes
import com.davidgcd.backlog.ui.components.StatCard
import com.davidgcd.backlog.ui.components.countLabelRes
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.components.label
import com.davidgcd.backlog.ui.components.tint
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.BookImage
import com.davidgcd.backlog.ui.components.MediaType
import com.davidgcd.backlog.ui.search.CrossSearch
import com.davidgcd.backlog.ui.search.CrossSearchState
import com.davidgcd.backlog.ui.search.crossSearchItems
import kotlinx.coroutines.flow.MutableStateFlow
import com.davidgcd.backlog.util.BookShareText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    viewModel: BooksViewModel,
    onBookClick: (String) -> Unit,
    crossSearch: CrossSearch? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val openLabel = stringResource(R.string.action_open)

    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    val visible by viewModel.visible.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val filter by viewModel.filter.collectAsState()
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
    fun runSearch(text: String) {
        viewModel.search(text)
        crossSearch?.viewModel?.search(text, MediaType.BOOKS)
    }
    val onCrossAdded: (String) -> Unit = { name ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_added, name), duration = SnackbarDuration.Short)
        }
    }

    fun closeSearch() {
        showSearch = false
        query = ""
        runSearch("")
    }

    BackHandler(enabled = showSearch) { closeSearch() }
    LaunchedEffect(showSearch) { if (showSearch) searchFocus.requestFocus() }

    var sharing by remember { mutableStateOf(false) }
    var showScopeDialog by remember { mutableStateOf(false) }

    val shareList: () -> Unit = {
        scope.launch {
            sharing = true
            try {
                val books = viewModel.booksToShare()
                val header = context.resources.getQuantityString(R.plurals.share_books_header, books.size, books.size)
                // A public page (covers, best note first) when the server answers; otherwise the plain-text list.
                val publicLink = viewModel.publishShareLink(header, books)
                val text = if (publicLink != null) {
                    "$header\n$publicLink"
                } else {
                    BookShareText.build(
                        books,
                        BookShareText.Labels(
                            header = { header },
                            status = { status -> context.getString(status.labelRes()) },
                            ratings = context.getString(R.string.share_books_ratings),
                        ),
                    )
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, context.getString(R.string.share_books_chooser)))
            } finally {
                sharing = false
            }
        }
    }

    val shareLibrary: () -> Unit = {
        scope.launch {
            sharing = true
            try {
                val header = context.getString(R.string.share_library_header)
                // No server reachable: fall back to this tab's plain text rather than sharing nothing.
                val publicLink = viewModel.publishLibraryLink(header)
                if (publicLink == null) {
                    shareList()
                } else {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "$header\n$publicLink")
                    }
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_library_chooser)))
                }
            } finally {
                sharing = false
            }
        }
    }

    if (showScopeDialog) {
        ShareScopeDialog(
            tabHintRes = R.string.share_scope_tab_books_hint,
            onLibrary = { showScopeDialog = false; shareLibrary() },
            onTab = { showScopeDialog = false; shareList() },
            onDismiss = { showScopeDialog = false },
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
                            stringResource(R.string.books_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showScopeDialog = true }, enabled = !sharing) {
                            if (sharing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Glass.Text)
                            } else {
                                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share_books))
                            }
                        }
                        SortMenuButton(current = sort, onSelect = viewModel::setSort)
                        FilterMenuButton(current = filter, availableSubjects = subjects, onChange = viewModel::setFilter)
                    }
                    IconButton(onClick = { if (showSearch) closeSearch() else showSearch = true }) {
                        Icon(
                            if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(if (showSearch) R.string.action_close_search else R.string.action_search),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (showSearch) {
                TextField(
                    value = query,
                    onValueChange = {
                        query = it
                        runSearch(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(searchFocus),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Glass.GlassStrong,
                        unfocusedContainerColor = Glass.GlassTop,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Glass.Cyan,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch(query) }),
                    placeholder = { Text(stringResource(R.string.books_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; runSearch("") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                )
                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Glass.Cyan, trackColor = Color.Transparent)
                } else {
                    Spacer(Modifier.height(4.dp))
                }
                searchError?.let { error ->
                    // Never the technical cause: only what the user can do about it.
                    Text(
                        text = stringResource(
                            when (error) {
                                SearchError.Network -> R.string.books_search_error_network
                                SearchError.Server -> R.string.books_search_error_server
                                SearchError.Unknown -> R.string.books_search_error_unknown
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
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
                EmptyState(
                    message = stringResource(R.string.books_empty),
                    actionLabel = stringResource(R.string.books_empty_cta),
                    onAction = { showSearch = true },
                )
            } else {
                BookGrid(
                    books = visible,
                    filter = filter,
                    statusCounts = statusCounts,
                    onFilterChange = viewModel::setFilter,
                    onStatusChange = viewModel::setStatus,
                    onClick = { onBookClick(it.bookKey) },
                )
            }
        }
    }
}

@Composable
private fun BookGrid(
    books: List<BookEntity>,
    filter: BookFilter,
    statusCounts: Map<ReadStatus, Int>,
    onFilterChange: (BookFilter) -> Unit,
    onStatusChange: (BookEntity, ReadStatus) -> Unit,
    onClick: (BookEntity) -> Unit,
) {
    LazyVerticalGrid(
        // Three covers across on a phone; the grid simply gets wider on a tablet or unfolded screen.
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(ReadStatus.TO_READ, ReadStatus.READING, ReadStatus.READ).forEach { status ->
                        StatCard(
                            value = (statusCounts[status] ?: 0).toString(),
                            label = stringResource(status.countLabelRes()),
                            modifier = Modifier.weight(1f),
                            accent = status.tint(),
                        )
                    }
                }
                val abandoned = statusCounts[ReadStatus.ABANDONED] ?: 0
                if (abandoned > 0) {
                    Text(
                        stringResource(R.string.books_abandoned_count, abandoned),
                        style = MaterialTheme.typography.labelMedium,
                        color = Glass.TextMuted,
                    )
                }
            }
        }
        if (filter.isActive) {
            item(span = { GridItemSpan(maxLineSpan) }) { ActiveFilterChips(filter, onFilterChange) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.section_my_books),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Glass.Text,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    message = stringResource(R.string.books_filtered_empty),
                    actionLabel = stringResource(R.string.filter_reset),
                    onAction = { onFilterChange(BookFilter()) },
                    fillScreen = false,
                )
            }
        }
        items(books, key = { it.bookKey }) { book ->
            BookCard(
                book = book,
                onClick = { onClick(book) },
                onStatusChange = { onStatusChange(book, it) },
            )
        }
    }
}

/** Cover, title, author and a status badge that opens the status menu — changing it takes two taps, no detail screen. */
@Composable
private fun BookCard(book: BookEntity, onClick: () -> Unit, onStatusChange: (ReadStatus) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
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
        Box {
            GlassBadge(
                book.readStatus.label(),
                tint = book.readStatus.tint(),
                modifier = Modifier.clickable { menuOpen = true },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ReadStatus.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label()) },
                        leadingIcon = { RadioButton(selected = option == book.readStatus, onClick = null) },
                        onClick = {
                            menuOpen = false
                            onStatusChange(option)
                        },
                    )
                }
            }
        }
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
    extra: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {},
) {
    // A fresh list state per first hit: new rows can never leave the list anchored below the tab's own results.
    val listState = androidx.compose.runtime.remember(results.firstOrNull()?.book?.key) { androidx.compose.foundation.lazy.LazyListState() }
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
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_to_books), tint = Glass.Cyan)
                }
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
        Text(stringResource(R.string.books_search_none), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.books_search_none_hint), style = MaterialTheme.typography.bodyMedium, color = Glass.TextMuted, textAlign = TextAlign.Center)
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

@Composable
private fun RemovableChip(label: String, onClear: () -> Unit) {
    GlassPill(
        text = label,
        selected = true,
        onClick = onClear,
        trailing = {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_clear),
                modifier = Modifier.size(16.dp),
                tint = Glass.Cyan,
            )
        },
    )
}

@Composable
private fun EmptyState(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    fillScreen: Boolean = true,
) {
    Column(
        modifier = (if (fillScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painter = painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(72.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
        GradientButton(text = actionLabel, onClick = onAction)
    }
}

@Composable
private fun SortMenuButton(current: BookSort, onSelect: (BookSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.action_sort))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        BookSort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes())) },
                leadingIcon = { RadioButton(selected = option == current, onClick = null) },
                onClick = {
                    onSelect(option)
                    expanded = false
                },
            )
        }
    }
}

private fun BookSort.labelRes(): Int = when (this) {
    BookSort.RECENTLY_ADDED -> R.string.sort_recently_added
    BookSort.TITLE -> R.string.sort_title
    BookSort.AUTHOR -> R.string.sort_author
    BookSort.PUBLISHED_YEAR -> R.string.sort_published_year
    BookSort.MY_RATING -> R.string.sort_my_rating
}

@Composable
private fun FilterMenuButton(current: BookFilter, availableSubjects: List<String>, onChange: (BookFilter) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            Icons.Filled.FilterList,
            contentDescription = stringResource(R.string.action_filter),
            tint = if (current.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.filter_favorites)) },
            trailingIcon = { Switch(checked = current.favoritesOnly, onCheckedChange = { onChange(current.copy(favoritesOnly = it)) }) },
            onClick = { onChange(current.copy(favoritesOnly = !current.favoritesOnly)) },
        )
        HorizontalDivider()
        MenuTitle(stringResource(R.string.filter_status))
        (listOf<ReadStatus?>(null) + ReadStatus.entries).forEach { option ->
            DropdownMenuItem(
                text = { Text(option?.label() ?: stringResource(R.string.filter_any)) },
                leadingIcon = { RadioButton(selected = current.status == option, onClick = null) },
                onClick = { onChange(current.copy(status = option)) },
            )
        }
        if (availableSubjects.isNotEmpty()) {
            HorizontalDivider()
            MenuTitle(stringResource(R.string.filter_genre))
            (listOf<String?>(null) + availableSubjects).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option ?: stringResource(R.string.filter_any)) },
                    leadingIcon = { RadioButton(selected = current.subject == option, onClick = null) },
                    onClick = { onChange(current.copy(subject = option)) },
                )
            }
        }
        if (current.isActive) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_reset)) },
                onClick = {
                    onChange(BookFilter())
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun MenuTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
