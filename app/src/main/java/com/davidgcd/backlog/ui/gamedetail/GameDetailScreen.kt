package com.davidgcd.backlog.ui.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.GameJsonCache
import com.davidgcd.backlog.data.local.gameStatus
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.label
import com.davidgcd.backlog.data.repository.MetacriticScore
import com.davidgcd.backlog.data.repository.SteamReviewSummary
import com.davidgcd.backlog.util.FrenchLabels
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.ui.components.GameCover
import com.davidgcd.backlog.ui.components.GlassBadge
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.components.GradientProgressBar
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.IgdbImage
import com.davidgcd.backlog.util.ReleaseDateFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(viewModel: GameDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val ratings by viewModel.ratings.collectAsState()
    val frenchSummary by viewModel.frenchSummary.collectAsState()

    val backdropId = when (val s = state) {
        is GameDetailState.InBacklog -> s.entity.coverImageId
        is GameDetailState.Remote -> s.game.cover?.imageId
        else -> null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The game's own artwork tints the screen: blurred, dimmed, faded into the night canvas.
        // (Blur is API 31+; older devices just get the dimmed, unblurred image.)
        if (backdropId != null) {
            Box(modifier = Modifier.fillMaxWidth().height(460.dp).clipToBounds()) {
                AsyncImage(
                    model = IgdbImage.url(backdropId, IgdbImage.Size.CoverBig),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().scale(1.2f).blur(28.dp).alpha(0.5f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0x26050A14), Glass.Bg))),
                )
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Glass.Text,
            topBar = {
                TopAppBar(
                    colors = glassTopAppBarColors(),
                    title = {},
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
                    is GameDetailState.Loading -> Centered { CircularProgressIndicator(color = Glass.Cyan) }
                    is GameDetailState.NotFound -> Centered { Text(stringResource(R.string.game_not_found)) }
                    is GameDetailState.InBacklog -> GameDetailContent(
                        display = current.entity.toDisplay().withSummary(frenchSummary),
                        ratings = ratings,
                        inBacklog = true,
                        isArchived = current.entity.isArchived,
                        status = current.entity.gameStatus,
                        onStatusChange = { viewModel.setStatus(current.entity, it) },
                        onArchiveToggle = { viewModel.setArchived(current.entity, !current.entity.isArchived) },
                        onRemove = { viewModel.remove(current.entity, onDone = onBack) },
                        onAdd = {},
                    )
                    is GameDetailState.Remote -> GameDetailContent(
                        display = current.game.toDisplay().withSummary(frenchSummary),
                        ratings = ratings,
                        inBacklog = false,
                        isArchived = false,
                        status = GameStatus.BACKLOG,
                        onStatusChange = {},
                        onArchiveToggle = {},
                        onRemove = {},
                        onAdd = { viewModel.addToBacklog(current.game) },
                    )
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameDetailContent(
    display: GameDisplay,
    ratings: RatingsState,
    inBacklog: Boolean,
    isArchived: Boolean,
    status: GameStatus,
    onStatusChange: (GameStatus) -> Unit,
    onArchiveToggle: () -> Unit,
    onRemove: () -> Unit,
    onAdd: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }

    // Centred, width-capped column so tablets/desktop don't stretch a phone layout.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero: big cover + title block.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                GameCover(display.coverImageId, width = 132.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = display.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Glass.Text,
                    )
                    ReleaseDateFormatting.format(display.firstReleaseDate)?.let { date ->
                        Text(date, style = MaterialTheme.typography.bodyMedium, color = Glass.TextMuted)
                    }
                    if (isArchived) GlassBadge(stringResource(R.string.label_archived), tint = Glass.Amber)
                }
            }

            if (display.platforms.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    display.platforms.forEach { GlassBadge(FrenchLabels.platform(it), tint = Glass.Cyan) }
                }
            }
            if (display.genres.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    display.genres.forEach { GlassBadge(FrenchLabels.genre(it), tint = Glass.Purple) }
                }
            }

            if (inBacklog) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.status_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = Glass.TextMuted,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameStatus.entries.forEach { option ->
                            GlassPill(text = option.label(), selected = option == status, onClick = { onStatusChange(option) })
                        }
                    }
                }
            }

            // Never renders "No score available" — a game with no score just has no section here,
            // same rule as the iOS app's Notes (GameRatingsSection): missing is not an error.
            if (display.totalRating != null || !ratings.isEmpty) {
                RatingsCard(display.totalRating, ratings)
            }

            display.summary?.let { summary ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Glass.Text.copy(alpha = 0.86f),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            // One clear primary action; the destructive one is visually demoted and confirmed.
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (inBacklog) {
                    GlassButton(
                        text = stringResource(if (isArchived) R.string.action_unarchive else R.string.action_archive),
                        onClick = onArchiveToggle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassButton(
                        text = stringResource(R.string.action_remove_from_backlog),
                        onClick = { confirmRemove = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                } else {
                    GradientButton(
                        text = stringResource(R.string.action_add_to_backlog),
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.remove_confirm_title)) },
            text = { Text(stringResource(R.string.remove_confirm_message, display.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** One glass card, one row per score that actually exists, each with a gradient bar. */
@Composable
private fun RatingsCard(igdbRating: Double?, ratings: RatingsState) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(R.string.ratings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            igdbRating?.let { ScoreRow(stringResource(R.string.ratings_igdb), "${it.toInt()}", it / 100.0) }
            if (ratings.isLoading) {
                CircularProgressIndicator(color = Glass.Cyan, modifier = Modifier.padding(top = 4.dp).height(20.dp))
            } else {
                ratings.metacritic?.let { MetacriticRow(it) }
                ratings.steam?.let { SteamRow(it) }
            }
        }
    }
}

@Composable
private fun ScoreRow(label: String, valueText: String, fraction: Double, caption: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Glass.TextMuted, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        GradientProgressBar(fraction.toFloat())
        // API wording (Steam verdicts, review counts) is shown as sent, never translated.
        caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted) }
    }
}

@Composable
private fun MetacriticRow(score: MetacriticScore) {
    ScoreRow(stringResource(R.string.ratings_metacritic), score.score.toString(), score.score / 100.0)
}

@Composable
private fun SteamRow(summary: SteamReviewSummary) {
    val count = stringResource(R.string.ratings_steam_review_count, summary.totalReviews)
    ScoreRow(
        label = stringResource(R.string.ratings_steam),
        valueText = "${summary.percentPositive}%",
        fraction = summary.percentPositive / 100.0,
        caption = listOfNotNull(summary.verdict, count).joinToString(" · "),
    )
}

/** Unifies GameEntity (persisted) and Game (IGDB response) for the detail screen's rendering. */
private data class GameDisplay(
    val name: String,
    val coverImageId: String?,
    val firstReleaseDate: Long?,
    val genres: List<String>,
    val platforms: List<String>,
    val summary: String?,
    val totalRating: Double?,
)

private fun GameDisplay.withSummary(french: String?) = if (french != null) copy(summary = french) else this

private fun GameEntity.toDisplay() = GameDisplay(
    name = name,
    coverImageId = coverImageId,
    firstReleaseDate = firstReleaseDate,
    genres = GameJsonCache.genreNames(this),
    platforms = GameJsonCache.platformNames(this),
    summary = summary,
    totalRating = totalRating,
)

private fun Game.toDisplay() = GameDisplay(
    name = name,
    coverImageId = cover?.imageId,
    firstReleaseDate = firstReleaseDate,
    genres = genres?.map { it.name } ?: emptyList(),
    platforms = platforms?.map { it.name } ?: emptyList(),
    summary = summary,
    totalRating = totalRating,
)
