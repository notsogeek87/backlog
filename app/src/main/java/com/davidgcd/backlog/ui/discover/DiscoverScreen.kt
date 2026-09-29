package com.davidgcd.backlog.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.util.IgdbImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(viewModel: DiscoverViewModel, onGameClick: (Long) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val backlogIds by viewModel.backlogIds.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discover_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                is DiscoverState.Loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }

                is DiscoverState.Error -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.discover_error),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }

                is DiscoverState.Loaded -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(current.games, key = { it.id }) { game ->
                        val inBacklog = backlogIds.contains(game.id)
                        ListItem(
                            modifier = Modifier.clickable { onGameClick(game.id) },
                            headlineContent = { Text(game.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                game.cover?.imageId?.let { imageId ->
                                    AsyncImage(
                                        model = IgdbImage.url(imageId, IgdbImage.Size.CoverSmall),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(48.dp, 64.dp).clip(RoundedCornerShape(6.dp)),
                                    )
                                }
                            },
                            trailingContent = {
                                if (inBacklog) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = stringResource(R.string.discover_already_in_backlog),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    // Same icon action as search results: consistent add affordance app-wide.
                                    IconButton(onClick = { viewModel.addToBacklog(game) }) {
                                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_to_backlog))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
