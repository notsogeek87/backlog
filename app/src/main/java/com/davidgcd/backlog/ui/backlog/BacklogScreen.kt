package com.davidgcd.backlog.ui.backlog

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.gameStatus
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.ui.components.label
import com.davidgcd.backlog.ui.components.labelRes
import com.davidgcd.backlog.util.BacklogShareText
import com.davidgcd.backlog.ui.components.tint
import com.davidgcd.backlog.data.local.GameJsonCache
import com.davidgcd.backlog.util.FrenchLabels
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.ui.components.GameCover
import com.davidgcd.backlog.ui.components.GameListItem
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.components.StatCard
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.ReleaseDateFormatting
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogScreen(
    viewModel: BacklogViewModel,
    onGameClick: (Long) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.action_undo)

    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    val visibleBacklog by viewModel.visibleBacklog.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val availableGenres by viewModel.availableGenres.collectAsState()
    val availablePlatforms by viewModel.availablePlatforms.collectAsState()
    val isBacklogEmpty by viewModel.isBacklogEmpty.collectAsState()
    val statusCounts by viewModel.statusCounts.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val backlogIds by viewModel.backlogIds.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()

    fun closeSearch() {
        showSearch = false
        query = ""
        viewModel.search("")
    }

    // System back leaves search mode first instead of exiting the app.
    BackHandler(enabled = showSearch) { closeSearch() }

    LaunchedEffect(showSearch) {
        if (showSearch) searchFocus.requestFocus()
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Glass.Text,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo_mark),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.backlog_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(enabled = !sharing, onClick = {
                            scope.launch {
                                sharing = true
                                try {
                                    val games = viewModel.gamesToShare()
                                    val links = viewModel.linksToShare(games)
                                    val header = context.resources.getQuantityString(
                                        R.plurals.share_backlog_header,
                                        games.count { !it.isArchived },
                                        games.count { !it.isArchived },
                                    )
                                    // A public page when the server answers; otherwise the plain-text list, still with IGDB links.
                                    val publicLink = viewModel.publishShareLink(header, games, links)
                                    val text = if (publicLink != null) {
                                        "$header\n$publicLink"
                                    } else {
                                        BacklogShareText.build(
                                            games,
                                            BacklogShareText.Labels(
                                                header = { header },
                                                status = { status -> context.getString(status.labelRes()) },
                                            ),
                                            links = links,
                                        )
                                    }
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_backlog_chooser)))
                                } finally {
                                    sharing = false
                                }
                            }
                        }) {
                            if (sharing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Glass.Text)
                            } else {
                                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share_backlog))
                            }
                        }
                        SortMenuButton(current = sort, onSelect = viewModel::setSort)
                        FilterMenuButton(
                            current = filter,
                            availableGenres = availableGenres,
                            availablePlatforms = availablePlatforms,
                            onChange = viewModel::setFilter,
                        )
                    }
                    IconButton(onClick = { if (showSearch) closeSearch() else showSearch = true }) {
                        Icon(
                            if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(
                                if (showSearch) R.string.action_close_search else R.string.action_search,
                            ),
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
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; viewModel.search("") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                )
                // Reserved height keeps results from jumping when the loader appears.
                if (isSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Glass.Cyan,
                        trackColor = Color.Transparent,
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                }
                searchError?.let { error ->
                    Text(
                        text = stringResource(
                            when (error) {
                                SearchError.Network -> R.string.search_error_network
                                SearchError.Server -> R.string.search_error_server
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
                SearchResultsList(
                    results = searchResults,
                    backlogIds = backlogIds,
                    onAdd = { game ->
                        viewModel.addToBacklog(game)
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.snackbar_added, game.name),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    onClick = { onGameClick(it.id) },
                )
            } else if (isBacklogEmpty) {
                EmptyState(
                    message = stringResource(R.string.backlog_empty),
                    actionLabel = stringResource(R.string.backlog_empty_cta),
                    onAction = { showSearch = true },
                )
            } else {
                BacklogGrid(
                    games = visibleBacklog,
                    filter = filter,
                    statusCounts = statusCounts,
                    recentlyAdded = recentlyAdded,
                    onFilterChange = viewModel::setFilter,
                    onArchiveToggle = { game ->
                        val archive = !game.isArchived
                        viewModel.setArchived(game, archive)
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                message = context.getString(
                                    if (archive) R.string.snackbar_archived else R.string.snackbar_unarchived,
                                    game.name,
                                ),
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.setArchived(game, !archive)
                        }
                    },
                    onClick = { onGameClick(it.igdbId) },
                )
            }
        }
    }
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

/**
 * Home + list in one adaptive grid: 1 column on phones, several on tablets/desktop.
 * Header blocks (stats, recent carousel, active filters) span the full width.
 */
@Composable
private fun BacklogGrid(
    games: List<GameEntity>,
    filter: BacklogFilter,
    statusCounts: Map<GameStatus, Int>,
    recentlyAdded: List<GameEntity>,
    onFilterChange: (BacklogFilter) -> Unit,
    onArchiveToggle: (GameEntity) -> Unit,
    onClick: (GameEntity) -> Unit,
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
                GameStatus.entries.forEach { status ->
                    StatCard(
                        value = (statusCounts[status] ?: 0).toString(),
                        label = status.label(),
                        modifier = Modifier.weight(1f),
                        accent = status.tint(),
                    )
                }
            }
        }
        if (!filter.isActive && recentlyAdded.isNotEmpty()) {
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
                                modifier = Modifier.clickable { onClick(game) },
                            )
                        }
                    }
                }
            }
        }
        if (filter.isActive) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ActiveFilterChips(filter = filter, onChange = onFilterChange)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(stringResource(R.string.section_my_games))
        }
        if (games.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    message = stringResource(R.string.backlog_filtered_empty),
                    actionLabel = stringResource(R.string.filter_reset),
                    onAction = { onFilterChange(BacklogFilter()) },
                    fillScreen = false,
                )
            }
        }
        items(games, key = { it.igdbId }) { game ->
            val supporting = listOfNotNull(
                ReleaseDateFormatting.format(game.firstReleaseDate),
                if (game.isArchived) stringResource(R.string.label_archived) else null,
            ).joinToString(" · ")
            GameListItem(
                name = game.name,
                coverImageId = game.coverImageId,
                platforms = GameJsonCache.platformNames(game).map(FrenchLabels::platform),
                meta = supporting,
                rating = game.totalRating,
                dimmed = game.isArchived,
                statusLabel = game.gameStatus.label(),
                statusTint = game.gameStatus.tint(),
                onClick = { onClick(game) },
                trailing = {
                    val label = stringResource(
                        if (game.isArchived) R.string.action_unarchive else R.string.action_archive,
                    )
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

/** Active filters stay visible (and one tap removable) after the menu closes. */
@Composable
private fun ActiveFilterChips(filter: BacklogFilter, onChange: (BacklogFilter) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filter.status?.let { RemovableChip(it.label()) { onChange(filter.copy(status = null)) } }
        filter.genre?.let { RemovableChip(FrenchLabels.genre(it)) { onChange(filter.copy(genre = null)) } }
        filter.platform?.let { RemovableChip(FrenchLabels.platform(it)) { onChange(filter.copy(platform = null)) } }
        if (filter.showArchived) {
            RemovableChip(stringResource(R.string.filter_show_archived)) { onChange(filter.copy(showArchived = false)) }
        }
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
private fun EmptyState(message: String, actionLabel: String, onAction: () -> Unit, fillScreen: Boolean = true) {
    Column(
        modifier = (if (fillScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_mark),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
        GradientButton(text = actionLabel, onClick = onAction)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenuButton(current: BacklogSort, onSelect: (BacklogSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.action_sort))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        BacklogSort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label()) },
                leadingIcon = { RadioButton(selected = option == current, onClick = null) },
                onClick = {
                    onSelect(option)
                    expanded = false
                },
            )
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
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterMenuButton(
    current: BacklogFilter,
    availableGenres: List<String>,
    availablePlatforms: List<String>,
    onChange: (BacklogFilter) -> Unit,
) {
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
            trailingIcon = {
                Switch(
                    checked = current.showArchived,
                    onCheckedChange = { onChange(current.copy(showArchived = it)) },
                )
            },
            onClick = { onChange(current.copy(showArchived = !current.showArchived)) },
        )
        HorizontalDivider()
        Text(
            stringResource(R.string.filter_status),
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        (listOf<GameStatus?>(null) + GameStatus.entries).forEach { option ->
            DropdownMenuItem(
                text = { Text(option?.label() ?: stringResource(R.string.filter_any)) },
                leadingIcon = { RadioButton(selected = current.status == option, onClick = null) },
                onClick = { onChange(current.copy(status = option)) },
            )
        }
        // Genre/platform values stay IGDB's raw names (filter identity); only the label is French.
        // Every option is listed (the menu scrolls) rather than truncated to a few chips.
        if (availableGenres.isNotEmpty()) {
            FilterSection(
                title = stringResource(R.string.filter_genre),
                options = availableGenres,
                label = FrenchLabels::genre,
                selected = current.genre,
                onSelect = { onChange(current.copy(genre = it)) },
            )
        }
        if (availablePlatforms.isNotEmpty()) {
            FilterSection(
                title = stringResource(R.string.filter_platform),
                options = availablePlatforms,
                label = FrenchLabels::platform,
                selected = current.platform,
                onSelect = { onChange(current.copy(platform = it)) },
            )
        }
        if (current.isActive) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_reset)) },
                onClick = {
                    onChange(BacklogFilter())
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun FilterSection(title: String, options: List<String>, label: (String) -> String, selected: String?, onSelect: (String?) -> Unit) {
    HorizontalDivider()
    Text(
        title,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    (listOf<String?>(null) + options).forEach { option ->
        DropdownMenuItem(
            text = { Text(option?.let(label) ?: stringResource(R.string.filter_any)) },
            leadingIcon = { RadioButton(selected = selected == option, onClick = null) },
            onClick = { onSelect(option) },
        )
    }
}

@Composable
private fun SearchResultsList(
    results: List<Game>,
    backlogIds: Set<Long>,
    onAdd: (Game) -> Unit,
    onClick: (Game) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
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
                        IconButton(onClick = { onAdd(game) }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.action_add_to_backlog),
                                tint = Glass.Cyan,
                            )
                        }
                    }
                },
            )
        }
    }
}
