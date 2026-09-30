package com.davidgcd.backlog.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.model.DiscoverCategory
import com.davidgcd.backlog.ui.components.GameListItem
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.ReleaseDateFormatting
import com.davidgcd.backlog.util.FrenchLabels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(viewModel: DiscoverViewModel, onGameClick: (Long) -> Unit, onBack: (() -> Unit)? = null) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val backlogIds by viewModel.backlogIds.collectAsState()
    val category by viewModel.category.collectAsState()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = Glass.Text,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = { Text(stringResource(R.string.discover_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DiscoverCategory.entries.forEach { option ->
                    GlassPill(text = option.label(), selected = option == category, onClick = { viewModel.select(option) })
                }
            }
            when (val current = state) {
                is DiscoverState.Loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator(color = Glass.Cyan) }

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
                    GlassButton(
                        text = stringResource(R.string.action_retry),
                        onClick = viewModel::refresh,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                // Keyed by category so switching lists starts back at the top.
                is DiscoverState.Loaded -> key(category) { LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(current.games, key = { it.id }) { game ->
                        val inBacklog = backlogIds.contains(game.id)
                        GameListItem(
                            name = game.name,
                            coverImageId = game.cover?.imageId,
                            platforms = game.platforms?.map { FrenchLabels.platform(it.name) } ?: emptyList(),
                            meta = ReleaseDateFormatting.format(game.firstReleaseDate),
                            onClick = { onGameClick(game.id) },
                            trailing = {
                                if (inBacklog) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = stringResource(R.string.discover_already_in_backlog),
                                        tint = Glass.Green,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                } else {
                                    // Same icon action as search results: consistent add affordance app-wide.
                                    IconButton(onClick = {
                                        viewModel.addToBacklog(game)
                                        scope.launch {
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_added, game.name))
                                        }
                                    }) {
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
                } }
            }
        }
    }
}

@Composable
private fun DiscoverCategory.label(): String = stringResource(
    when (this) {
        DiscoverCategory.POPULAR -> R.string.discover_popular
        DiscoverCategory.TOP_RATED -> R.string.discover_top_rated
        DiscoverCategory.TRENDING -> R.string.discover_trending
        DiscoverCategory.NEW_RELEASES -> R.string.discover_new_releases
        DiscoverCategory.UPCOMING -> R.string.discover_upcoming
    },
)
