package com.davidgcd.backlog.ui.backlog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.util.IgdbImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogScreen(viewModel: BacklogViewModel, onGameClick: (Long) -> Unit, onSettingsClick: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val visibleBacklog by viewModel.visibleBacklog.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val availableGenres by viewModel.availableGenres.collectAsState()
    val availablePlatforms by viewModel.availablePlatforms.collectAsState()
    val isBacklogEmpty by viewModel.isBacklogEmpty.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backlog") },
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
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
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
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    placeholder = { Text("Search IGDB…") },
                )
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
                SearchResultsList(
                    results = searchResults,
                    onAdd = { viewModel.addToBacklog(it) },
                    onClick = { onGameClick(it.id) },
                )
            } else {
                if (isBacklogEmpty) {
                    EmptyBacklog()
                } else {
                    BacklogList(
                        games = visibleBacklog,
                        onArchiveToggle = { viewModel.setArchived(it, !it.isArchived) },
                        onRemove = { viewModel.remove(it) },
                        onClick = { onGameClick(it.igdbId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBacklog() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Your backlog is empty. Search a game to add it.",
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenuButton(current: BacklogSort, onSelect: (BacklogSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.Sort, contentDescription = "Sort")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        BacklogSort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                leadingIcon = { RadioButton(selected = option == current, onClick = null) },
                onClick = {
                    onSelect(option)
                    expanded = false
                },
            )
        }
    }
}

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
            contentDescription = "Filter",
            tint = if (current.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Show archived") },
            trailingIcon = {
                Switch(
                    checked = current.showArchived,
                    onCheckedChange = { onChange(current.copy(showArchived = it)) },
                )
            },
            onClick = { onChange(current.copy(showArchived = !current.showArchived)) },
        )
        if (availableGenres.isNotEmpty()) {
            Text("Genre", modifier = Modifier.padding(start = 12.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium)
            FilterOptionRow(
                options = availableGenres,
                selected = current.genre,
                onSelect = { onChange(current.copy(genre = it)) },
            )
        }
        if (availablePlatforms.isNotEmpty()) {
            Text("Platform", modifier = Modifier.padding(start = 12.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium)
            FilterOptionRow(
                options = availablePlatforms,
                selected = current.platform,
                onSelect = { onChange(current.copy(platform = it)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterOptionRow(options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("Any") },
            modifier = Modifier.padding(end = 4.dp),
        )
        options.take(6).forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(if (selected == option) null else option) },
                label = { Text(option) },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

@Composable
private fun BacklogList(
    games: List<GameEntity>,
    onArchiveToggle: (GameEntity) -> Unit,
    onRemove: (GameEntity) -> Unit,
    onClick: (GameEntity) -> Unit,
) {
    if (games.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Your backlog is empty. Search a game to add it.",
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(games, key = { it.igdbId }) { game ->
            ListItem(
                modifier = Modifier.clickable { onClick(game) },
                headlineContent = { Text(game.name) },
                supportingContent = { if (game.isArchived) Text("Archived") },
                leadingContent = {
                    game.coverImageId?.let { imageId ->
                        AsyncImage(
                            model = IgdbImage.url(imageId, IgdbImage.Size.CoverSmall),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp, 64.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    }
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onArchiveToggle(game) }) {
                            Text(if (game.isArchived) "Unarchive" else "Archive")
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SearchResultsList(results: List<Game>, onAdd: (Game) -> Unit, onClick: (Game) -> Unit) {
    LazyColumn {
        items(results, key = { it.id }) { game ->
            ListItem(
                modifier = Modifier.clickable { onClick(game) },
                headlineContent = { Text(game.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    game.cover?.imageId?.let { imageId ->
                        AsyncImage(
                            model = IgdbImage.url(imageId, IgdbImage.Size.CoverSmall),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp, 64.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    }
                },
                trailingContent = {
                    IconButton(onClick = { onAdd(game) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add to backlog")
                    }
                },
            )
        }
    }
}
