package com.davidgcd.backlog.ui.backlog

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
fun BacklogScreen(viewModel: BacklogViewModel) {
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val backlog by viewModel.backlog.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backlog") },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
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
                )
            } else {
                BacklogList(
                    games = backlog,
                    onArchiveToggle = { viewModel.setArchived(it, !it.isArchived) },
                    onRemove = { viewModel.remove(it) },
                )
            }
        }
    }
}

@Composable
private fun BacklogList(
    games: List<GameEntity>,
    onArchiveToggle: (GameEntity) -> Unit,
    onRemove: (GameEntity) -> Unit,
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
private fun SearchResultsList(results: List<Game>, onAdd: (Game) -> Unit) {
    LazyColumn {
        items(results, key = { it.id }) { game ->
            ListItem(
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
