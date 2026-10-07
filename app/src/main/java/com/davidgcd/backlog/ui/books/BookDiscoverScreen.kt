package com.davidgcd.backlog.ui.books

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
import com.davidgcd.backlog.ui.components.SkeletonList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.davidgcd.backlog.data.repository.BookAddResult
import com.davidgcd.backlog.model.BookChart
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import kotlinx.coroutines.launch

/** Discover for books: the Open Library lists as pills, same layout as the films' and games' Discover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDiscoverScreen(
    viewModel: BookDiscoverViewModel,
    onBookClick: (String) -> Unit,
    mediaSwitch: @Composable () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val hits by viewModel.hits.collectAsState()
    val chart by viewModel.chart.collectAsState()
    val openLabel = stringResource(R.string.action_open)

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
                BookChart.entries.forEach { option ->
                    GlassPill(text = stringResource(option.labelRes()), selected = option == chart, onClick = { viewModel.select(option) })
                }
            }
            when (state) {
                is BookDiscoverState.Loading -> SkeletonList()

                is BookDiscoverState.Error -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Never the technical cause: only what the reader can do about it.
                    Text(
                        stringResource(R.string.book_discover_error),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    GlassButton(
                        text = stringResource(R.string.action_retry),
                        onClick = viewModel::refresh,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                // Keyed by list so switching starts back at the top.
                is BookDiscoverState.Loaded -> key(chart) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(hits, key = { it.book.key }) { hit ->
                            BookHitItem(
                                hit = hit,
                                onAdd = {
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
                                onClick = { onBookClick(hit.savedKey ?: hit.book.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun BookChart.labelRes(): Int = when (this) {
    BookChart.TRENDING -> R.string.book_chart_trending
    BookChart.SCIENCE_FICTION -> R.string.book_chart_science_fiction
    BookChart.FANTASY -> R.string.book_chart_fantasy
    BookChart.MYSTERY -> R.string.book_chart_mystery
    BookChart.ROMANCE -> R.string.book_chart_romance
    BookChart.CLASSICS -> R.string.book_chart_classics
}
