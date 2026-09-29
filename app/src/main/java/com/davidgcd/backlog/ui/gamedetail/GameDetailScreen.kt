package com.davidgcd.backlog.ui.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.GameJsonCache
import com.davidgcd.backlog.data.repository.MetacriticScore
import com.davidgcd.backlog.data.repository.SteamReviewSummary
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.util.IgdbImage
import com.davidgcd.backlog.util.ReleaseDateFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(viewModel: GameDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val ratings by viewModel.ratings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val name = when (val s = state) {
                        is GameDetailState.InBacklog -> s.entity.name
                        is GameDetailState.Remote -> s.game.name
                        else -> ""
                    }
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
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
                is GameDetailState.Loading -> Centered { CircularProgressIndicator() }
                is GameDetailState.NotFound -> Centered { Text(stringResource(R.string.game_not_found)) }
                is GameDetailState.InBacklog -> GameDetailContent(
                    display = current.entity.toDisplay(),
                    ratings = ratings,
                    inBacklog = true,
                    isArchived = current.entity.isArchived,
                    onArchiveToggle = { viewModel.setArchived(current.entity, !current.entity.isArchived) },
                    onRemove = { viewModel.remove(current.entity, onDone = onBack) },
                    onAdd = {},
                )
                is GameDetailState.Remote -> GameDetailContent(
                    display = current.game.toDisplay(),
                    ratings = ratings,
                    inBacklog = false,
                    isArchived = false,
                    onArchiveToggle = {},
                    onRemove = {},
                    onAdd = { viewModel.addToBacklog(current.game) },
                )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun GameDetailContent(
    display: GameDisplay,
    ratings: RatingsState,
    inBacklog: Boolean,
    isArchived: Boolean,
    onArchiveToggle: () -> Unit,
    onRemove: () -> Unit,
    onAdd: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        display.coverImageId?.let { imageId ->
            // IGDB covers are 3:4 portrait; full-width they'd fill the whole screen on phones,
            // so cap the width and centre it to keep the title and actions above the fold.
            AsyncImage(
                model = IgdbImage.url(imageId, IgdbImage.Size.CoverBig),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 220.dp)
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }

        Text(
            text = display.name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp),
        )

        ReleaseDateFormatting.format(display.firstReleaseDate)?.let { date ->
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (display.genres.isNotEmpty()) {
            Text(
                text = display.genres.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        display.summary?.let { summary ->
            Text(text = summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
        }

        // Never renders "No score available" — a game with neither score just has no section here,
        // same rule as the iOS app's Notes (GameRatingsSection): missing is not an error.
        if (!ratings.isEmpty) {
            RatingsSection(ratings, modifier = Modifier.padding(top = 16.dp))
        }

        if (display.platforms.isNotEmpty()) {
            Text(
                text = stringResource(R.string.details_platforms_prefix, display.platforms.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        // One clear primary action; the destructive one is visually demoted and confirmed.
        Column(modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) {
            if (inBacklog) {
                FilledTonalButton(onClick = onArchiveToggle, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (isArchived) R.string.action_unarchive else R.string.action_archive))
                }
                TextButton(
                    onClick = { confirmRemove = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.action_remove_from_backlog))
                }
            } else {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_add_to_backlog))
                }
            }
        }

        if (confirmRemove) {
            AlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = { Text(stringResource(R.string.remove_confirm_title)) },
                text = { Text(stringResource(R.string.remove_confirm_message, display.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmRemove = false
                            onRemove()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.action_remove)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}

@Composable
private fun RatingsSection(ratings: RatingsState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = stringResource(R.string.ratings_title), style = MaterialTheme.typography.titleMedium)
        if (ratings.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp).size(20.dp))
        } else {
            Row(modifier = Modifier.padding(top = 8.dp)) {
                ratings.metacritic?.let { MetacriticCard(it, modifier = Modifier.padding(end = 8.dp)) }
                ratings.steam?.let { SteamCard(it) }
            }
        }
    }
}

@Composable
private fun MetacriticCard(score: MetacriticScore, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = stringResource(R.string.ratings_metacritic), style = MaterialTheme.typography.labelMedium)
            Text(text = score.score.toString(), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun SteamCard(summary: SteamReviewSummary, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = stringResource(R.string.ratings_steam), style = MaterialTheme.typography.labelMedium)
            Text(text = "${summary.percentPositive}%", style = MaterialTheme.typography.headlineSmall)
            // Steam's own verdict wording ("Overwhelmingly Positive"…) — API content, never translated.
            summary.verdict?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            Text(
                text = stringResource(R.string.ratings_steam_review_count, summary.totalReviews),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Unifies GameEntity (persisted) and Game (IGDB response) for the detail screen's rendering. */
private data class GameDisplay(
    val name: String,
    val coverImageId: String?,
    val firstReleaseDate: Long?,
    val genres: List<String>,
    val platforms: List<String>,
    val summary: String?,
)

private fun GameEntity.toDisplay() = GameDisplay(
    name = name,
    coverImageId = coverImageId,
    firstReleaseDate = firstReleaseDate,
    genres = GameJsonCache.genreNames(this),
    platforms = GameJsonCache.platformNames(this),
    summary = summary,
)

private fun Game.toDisplay() = GameDisplay(
    name = name,
    coverImageId = cover?.imageId,
    firstReleaseDate = firstReleaseDate,
    genres = genres?.map { it.name } ?: emptyList(),
    platforms = platforms?.map { it.name } ?: emptyList(),
    summary = summary,
)
