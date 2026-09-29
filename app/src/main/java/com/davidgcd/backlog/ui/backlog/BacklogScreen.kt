package com.davidgcd.backlog.ui.backlog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.util.IgdbImage
import com.davidgcd.backlog.util.ReleaseDateFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogScreen(
    viewModel: BacklogViewModel,
    onGameClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onDiscoverClick: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    val visibleBacklog by viewModel.visibleBacklog.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val availableGenres by viewModel.availableGenres.collectAsState()
    val availablePlatforms by viewModel.availablePlatforms.collectAsState()
    val isBacklogEmpty by viewModel.isBacklogEmpty.collectAsState()
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backlog_title)) },
                actions = {
                    if (!showSearch) {
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
                    if (!showSearch) {
                        IconButton(onClick = onDiscoverClick) {
                            Icon(Icons.Filled.Explore, contentDescription = stringResource(R.string.action_discover))
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                        }
                    }
                },
            )
        },
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
                    shape = RoundedCornerShape(28.dp),
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
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    Spacer(Modifier.height(4.dp))
                }
                searchError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (!isSearching && searchError == null && query.isNotBlank() && searchResults.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
                SearchResultsList(
                    results = searchResults,
                    backlogIds = backlogIds,
                    onAdd = { viewModel.addToBacklog(it) },
                    onClick = { onGameClick(it.id) },
                )
            } else {
                if (filter.isActive) {
                    ActiveFilterChips(filter = filter, onChange = viewModel::setFilter)
                }
                if (isBacklogEmpty) {
                    EmptyState(
                        message = stringResource(R.string.backlog_empty),
                        actionLabel = stringResource(R.string.backlog_empty_cta),
                        onAction = { showSearch = true },
                    )
                } else if (visibleBacklog.isEmpty()) {
                    EmptyState(
                        message = stringResource(R.string.backlog_filtered_empty),
                        actionLabel = stringResource(R.string.filter_reset),
                        onAction = { viewModel.setFilter(BacklogFilter()) },
                    )
                } else {
                    BacklogList(
                        games = visibleBacklog,
                        onArchiveToggle = { viewModel.setArchived(it, !it.isArchived) },
                        onClick = { onGameClick(it.igdbId) },
                    )
                }
            }
        }
    }
}

/** Active filters stay visible (and one tap removable) after the menu closes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveFilterChips(filter: BacklogFilter, onChange: (BacklogFilter) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filter.genre?.let { RemovableChip(it) { onChange(filter.copy(genre = null)) } }
        filter.platform?.let { RemovableChip(it) { onChange(filter.copy(platform = null)) } }
        if (filter.showArchived) {
            RemovableChip(stringResource(R.string.filter_show_archived)) { onChange(filter.copy(showArchived = false)) }
        }
    }
}

@Composable
private fun EmptyState(message: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.LibraryAdd,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemovableChip(label: String, onClear: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onClear,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_clear),
                modifier = Modifier.size(18.dp),
            )
        },
    )
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
        // Genre/platform names come straight from IGDB and are never translated,
        // same rule as the iOS app leaving API content (genres, statuses) as sent.
        // Every option is listed (the menu scrolls) rather than truncated to a few chips.
        if (availableGenres.isNotEmpty()) {
            FilterSection(
                title = stringResource(R.string.filter_genre),
                options = availableGenres,
                selected = current.genre,
                onSelect = { onChange(current.copy(genre = it)) },
            )
        }
        if (availablePlatforms.isNotEmpty()) {
            FilterSection(
                title = stringResource(R.string.filter_platform),
                options = availablePlatforms,
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
private fun FilterSection(title: String, options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    HorizontalDivider()
    Text(
        title,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    (listOf<String?>(null) + options).forEach { option ->
        DropdownMenuItem(
            text = { Text(option ?: stringResource(R.string.filter_any)) },
            leadingIcon = { RadioButton(selected = selected == option, onClick = null) },
            onClick = { onSelect(option) },
        )
    }
}

@Composable
private fun BacklogList(
    games: List<GameEntity>,
    onArchiveToggle: (GameEntity) -> Unit,
    onClick: (GameEntity) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(games, key = { it.igdbId }) { game ->
            val releaseDate = ReleaseDateFormatting.format(game.firstReleaseDate)
            val supporting = listOfNotNull(
                releaseDate,
                if (game.isArchived) stringResource(R.string.label_archived) else null,
            ).joinToString(" · ")
            ListItem(
                // Archived games stay readable but recede behind active ones.
                modifier = Modifier.clickable { onClick(game) }.alpha(if (game.isArchived) 0.6f else 1f),
                headlineContent = { Text(game.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = { if (supporting.isNotEmpty()) Text(supporting) },
                leadingContent = { Cover(game.coverImageId) },
                trailingContent = {
                    val label = stringResource(
                        if (game.isArchived) R.string.action_unarchive else R.string.action_archive,
                    )
                    IconButton(onClick = { onArchiveToggle(game) }) {
                        Icon(
                            if (game.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                            contentDescription = label,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<Game>,
    backlogIds: Set<Long>,
    onAdd: (Game) -> Unit,
    onClick: (Game) -> Unit,
) {
    LazyColumn {
        items(results, key = { it.id }) { game ->
            ListItem(
                modifier = Modifier.clickable { onClick(game) },
                headlineContent = { Text(game.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    ReleaseDateFormatting.format(game.firstReleaseDate)?.let { Text(it) }
                },
                leadingContent = { Cover(game.cover?.imageId) },
                trailingContent = {
                    if (backlogIds.contains(game.id)) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.discover_already_in_backlog),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        IconButton(onClick = { onAdd(game) }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_to_backlog))
                        }
                    }
                },
            )
        }
    }
}

/** Fixed-size cover slot: rows without art keep the same alignment as rows with it. */
@Composable
private fun Cover(imageId: String?) {
    val shape = RoundedCornerShape(6.dp)
    if (imageId != null) {
        AsyncImage(
            model = IgdbImage.url(imageId, IgdbImage.Size.CoverSmall),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp, 64.dp).clip(shape),
        )
    } else {
        Box(
            modifier = Modifier.size(48.dp, 64.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
