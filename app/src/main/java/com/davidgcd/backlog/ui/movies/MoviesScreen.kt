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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Unarchive
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.local.titleKind
import com.davidgcd.backlog.data.local.watchStatus
import com.davidgcd.backlog.model.MediaTitle
import com.davidgcd.backlog.model.TitleKind
import com.davidgcd.backlog.model.WatchStatus
import com.davidgcd.backlog.ui.backlog.SearchError
import com.davidgcd.backlog.ui.components.GameListItem
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.components.StatCard
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.components.label
import com.davidgcd.backlog.ui.components.labelRes
import com.davidgcd.backlog.ui.components.tint
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.TmdbImage
import com.davidgcd.backlog.ui.components.ShareScopeDialog
import com.davidgcd.backlog.util.MovieShareText
import com.davidgcd.backlog.util.ReleaseDateFormatting
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(
    viewModel: MoviesViewModel,
    onMovieClick: (String) -> Unit,
    onOpenRanking: () -> Unit,
    onOpenTmdbImport: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.action_undo)

    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    val visible by viewModel.visible.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val genres by viewModel.availableGenres.collectAsState()
    val isEmpty by viewModel.isEmpty.collectAsState()
    val statusCounts by viewModel.statusCounts.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()

    fun closeSearch() {
        showSearch = false
        query = ""
        viewModel.search("")
    }

    BackHandler(enabled = showSearch) { closeSearch() }
    LaunchedEffect(showSearch) { if (showSearch) searchFocus.requestFocus() }

    var showScopeDialog by remember { mutableStateOf(false) }

    val shareList: () -> Unit = {
        scope.launch {
            sharing = true
            try {
                val movies = viewModel.titlesToShare()
                val count = movies.count { !it.isArchived }
                val header = context.resources.getQuantityString(R.plurals.share_movies_header, count, count)
                // A public page (posters, best note first) when the server answers; otherwise the plain-text list.
                val publicLink = viewModel.publishShareLink(header, movies)
                val text = if (publicLink != null) {
                    "$header\n$publicLink"
                } else {
                    MovieShareText.build(
                        movies,
                        MovieShareText.Labels(
                            header = { header },
                            status = { status -> context.getString(status.labelRes()) },
                            ranking = context.getString(R.string.share_backlog_ranking),
                        ),
                    )
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, context.getString(R.string.share_movies_chooser)))
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
            tabHintRes = R.string.share_scope_tab_movies_hint,
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
                            stringResource(R.string.movies_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = onOpenRanking) {
                            Icon(Icons.Filled.Leaderboard, contentDescription = stringResource(R.string.action_my_ranking))
                        }
                        IconButton(onClick = { showScopeDialog = true }, enabled = !sharing) {
                            if (sharing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Glass.Text)
                            } else {
                                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share_movies))
                            }
                        }
                        SortMenuButton(current = sort, onSelect = viewModel::setSort)
                        FilterMenuButton(current = filter, availableGenres = genres, onChange = viewModel::setFilter)
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
                        viewModel.search(it)
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
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
                    placeholder = { Text(stringResource(R.string.movies_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; viewModel.search("") }) {
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
                    Text(
                        text = stringResource(
                            when (error) {
                                SearchError.Network -> R.string.search_error_network
                                SearchError.Server -> R.string.movies_search_error_tmdb
                                SearchError.Unknown -> R.string.search_error_unknown
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (!isSearching && searchError == null && query.isNotBlank() && searchResults.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Glass.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
                SearchResults(
                    results = searchResults,
                    savedIds = savedIds,
                    onAdd = { title ->
                        viewModel.add(title)
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.snackbar_added, title.title),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    onClick = { onMovieClick(it.id) },
                )
            } else if (isEmpty) {
                EmptyState(
                    message = stringResource(R.string.movies_empty),
                    actionLabel = stringResource(R.string.movies_empty_cta),
                    onAction = { showSearch = true },
                    secondaryLabel = stringResource(R.string.movies_empty_import),
                    onSecondary = onOpenTmdbImport,
                )
            } else {
                MovieGrid(
                    movies = visible,
                    filter = filter,
                    statusCounts = statusCounts,
                    recentlyAdded = recentlyAdded,
                    onFilterChange = viewModel::setFilter,
                    onArchiveToggle = { movie ->
                        val archive = !movie.isArchived
                        viewModel.setArchived(movie, archive)
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                message = context.getString(
                                    if (archive) R.string.snackbar_archived else R.string.snackbar_unarchived,
                                    movie.title,
                                ),
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.setArchived(movie, !archive)
                        }
                    },
                    onClick = { onMovieClick(it.titleKey) },
                )
            }
        }
    }
}

@Composable
private fun MovieGrid(
    movies: List<MovieEntity>,
    filter: MovieFilter,
    statusCounts: Map<WatchStatus, Int>,
    recentlyAdded: List<MovieEntity>,
    onFilterChange: (MovieFilter) -> Unit,
    onArchiveToggle: (MovieEntity) -> Unit,
    onClick: (MovieEntity) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WatchStatus.entries.forEach { status ->
                    StatCard(
                        value = (statusCounts[status] ?: 0).toString(),
                        label = status.label(),
                        modifier = Modifier.weight(1f),
                        accent = status.tint(),
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
                            com.davidgcd.backlog.ui.components.GameCover(
                                imageId = null,
                                imageUrl = TmdbImage.poster(movie.posterUrl),
                                width = 112.dp,
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
        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.section_my_movies)) }
        if (movies.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    message = stringResource(R.string.movies_filtered_empty),
                    actionLabel = stringResource(R.string.filter_reset),
                    onAction = { onFilterChange(MovieFilter(kind = filter.kind)) },
                    fillScreen = false,
                )
            }
        }
        items(movies, key = { it.titleKey }) { movie ->
            MovieListItem(
                movie = movie,
                onClick = { onClick(movie) },
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

/** One saved title as a list row — shared by the list and the ranking screen. */
@Composable
fun MovieListItem(
    movie: MovieEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        userRatingLabel = movie.userRating?.let { stringResource(R.string.movie_my_rating_badge, it) },
        onClick = onClick,
        trailing = trailing,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Glass.Text,
        modifier = Modifier.padding(top = 8.dp),
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
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
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
        if (secondaryLabel != null) {
            com.davidgcd.backlog.ui.components.GlassButton(
                text = secondaryLabel,
                onClick = onSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun SortMenuButton(current: MovieSort, onSelect: (MovieSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.action_sort))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        MovieSort.entries.forEach { option ->
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

private fun MovieSort.labelRes(): Int = when (this) {
    MovieSort.RECENTLY_ADDED -> R.string.sort_recently_added
    MovieSort.NAME -> R.string.sort_name
    MovieSort.RELEASE_DATE -> R.string.sort_release_date
    MovieSort.TMDB_RATING -> R.string.sort_tmdb_rating
    MovieSort.MY_RATING -> R.string.sort_my_rating
    MovieSort.MY_RANKING -> R.string.sort_my_ranking
}

@Composable
private fun FilterMenuButton(current: MovieFilter, availableGenres: List<String>, onChange: (MovieFilter) -> Unit) {
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
            text = { Text(stringResource(R.string.filter_show_archived)) },
            trailingIcon = { Switch(checked = current.showArchived, onCheckedChange = { onChange(current.copy(showArchived = it)) }) },
            onClick = { onChange(current.copy(showArchived = !current.showArchived)) },
        )
        HorizontalDivider()
        MenuTitle(stringResource(R.string.filter_status))
        (listOf<WatchStatus?>(null) + WatchStatus.entries).forEach { option ->
            DropdownMenuItem(
                text = { Text(option?.label() ?: stringResource(R.string.filter_any)) },
                leadingIcon = { RadioButton(selected = current.status == option, onClick = null) },
                onClick = { onChange(current.copy(status = option)) },
            )
        }
        if (availableGenres.isNotEmpty()) {
            HorizontalDivider()
            MenuTitle(stringResource(R.string.filter_genre))
            (listOf<String?>(null) + availableGenres).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option ?: stringResource(R.string.filter_any)) },
                    leadingIcon = { RadioButton(selected = current.genre == option, onClick = null) },
                    onClick = { onChange(current.copy(genre = option)) },
                )
            }
        }
        if (current.isActive) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_reset)) },
                onClick = {
                    onChange(MovieFilter(kind = current.kind))
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

@Composable
private fun SearchResults(
    results: List<MediaTitle>,
    savedIds: Set<String>,
    onAdd: (MediaTitle) -> Unit,
    onClick: (MediaTitle) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(results, key = { it.id }) { title ->
            MediaTitleListItem(title = title, saved = title.id in savedIds, onAdd = { onAdd(title) }, onClick = { onClick(title) })
        }
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
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_to_movies), tint = Glass.Cyan)
                }
            }
        },
    )
}
