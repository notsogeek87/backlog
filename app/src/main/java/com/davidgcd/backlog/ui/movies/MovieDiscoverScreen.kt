package com.davidgcd.backlog.ui.movies

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.MovieChart
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import kotlinx.coroutines.launch

/** Discover for films & séries: the TMDB charts as pills, same layout as the games' Discover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDiscoverScreen(
    viewModel: MovieDiscoverViewModel,
    onMovieClick: (String) -> Unit,
    mediaSwitch: @Composable () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val chart by viewModel.chart.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Glass.Text,
        topBar = { TopAppBar(colors = glassTopAppBarColors(), title = { Text(stringResource(R.string.discover_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            mediaSwitch()
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MovieChart.entries.forEach { option ->
                    GlassPill(text = stringResource(option.labelRes()), selected = option == chart, onClick = { viewModel.select(option) })
                }
            }
            when (val current = state) {
                is MovieDiscoverState.Loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator(color = Glass.Cyan) }

                is MovieDiscoverState.Error -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.movie_discover_error),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    GlassButton(
                        text = stringResource(R.string.action_retry),
                        onClick = viewModel::refresh,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                // Keyed by chart so switching lists starts back at the top.
                is MovieDiscoverState.Loaded -> key(chart) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(current.titles, key = { it.id }) { title ->
                            MediaTitleListItem(
                                title = title,
                                saved = title.id in savedIds,
                                onAdd = {
                                    viewModel.add(title)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(context.getString(R.string.snackbar_added, title.title))
                                    }
                                },
                                onClick = { onMovieClick(title.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun MovieChart.labelRes(): Int = when (this) {
    MovieChart.POPULAR_MOVIES -> R.string.movie_chart_popular_movies
    MovieChart.TOP_MOVIES -> R.string.movie_chart_top_movies
    MovieChart.POPULAR_SERIES -> R.string.movie_chart_popular_series
    MovieChart.TOP_SERIES -> R.string.movie_chart_top_series
}
