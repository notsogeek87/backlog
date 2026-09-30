package com.davidgcd.backlog.ui.movies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieRankingScreen(
    viewModel: MovieRankingViewModel,
    onBack: () -> Unit,
    onMovieClick: (String) -> Unit,
) {
    val ordered by viewModel.ordered.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Glass.Text,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = { Text(stringResource(R.string.ranking_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (ordered.isEmpty()) {
            Text(
                stringResource(R.string.movie_ranking_empty),
                modifier = Modifier.padding(padding).padding(24.dp),
                color = Glass.TextMuted,
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.movie_ranking_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.TextMuted,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            itemsIndexed(ordered, key = { _, movie -> movie.titleKey }) { index, movie ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${index + 1}",
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (movie.userRank != null) Glass.Cyan else Glass.TextMuted,
                    )
                    MovieListItem(
                        movie = movie,
                        modifier = Modifier.weight(1f),
                        onClick = { onMovieClick(movie.titleKey) },
                        trailing = {
                            Column {
                                IconButton(modifier = Modifier.size(36.dp), enabled = index > 0, onClick = { viewModel.moveToTop(index) }) {
                                    Icon(Icons.Filled.KeyboardDoubleArrowUp, contentDescription = stringResource(R.string.ranking_move_top))
                                }
                                IconButton(modifier = Modifier.size(36.dp), enabled = index < ordered.lastIndex, onClick = { viewModel.moveToBottom(index) }) {
                                    Icon(Icons.Filled.KeyboardDoubleArrowDown, contentDescription = stringResource(R.string.ranking_move_bottom))
                                }
                            }
                            Column {
                                IconButton(modifier = Modifier.size(36.dp), enabled = index > 0, onClick = { viewModel.move(index, -1) }) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.ranking_move_up))
                                }
                                IconButton(modifier = Modifier.size(36.dp), enabled = index < ordered.lastIndex, onClick = { viewModel.move(index, 1) }) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.ranking_move_down))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
