package com.davidgcd.backlog.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.Medium
import com.davidgcd.backlog.model.ReviewItem
import com.davidgcd.backlog.ui.components.BookCover
import com.davidgcd.backlog.ui.components.GameCover
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.BookImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** « Réviser mes notes » : une fiche à la fois, de la note la plus ancienne à la plus récente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingReviewScreen(viewModel: RatingReviewViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Glass.Text,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = {
                    Column {
                        Text(stringResource(R.string.review_title))
                        if (!state.isLoading && state.queue.isNotEmpty() && !state.isFinished) {
                            Text(
                                stringResource(R.string.review_progress, state.position + 1, state.queue.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = Glass.TextMuted,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val current = state.current
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(color = Glass.Cyan)
                state.queue.isEmpty() -> Text(stringResource(R.string.review_empty), style = MaterialTheme.typography.bodyLarge, color = Glass.TextMuted)
                current == null -> FinishedCard(state.rated, state.queue.size, onRestart = viewModel::start, onBack = onBack)
                else -> ReviewCard(current, onRate = viewModel::rate, onSkip = viewModel::skip)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(item: ReviewItem, onRate: (Int) -> Unit, onSkip: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(
                    when (item.medium) {
                        Medium.GAME -> R.string.recap_games
                        Medium.MOVIE -> R.string.recap_movies
                        Medium.BOOK -> R.string.recap_books
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Glass.Cyan,
            )
            when (item.medium) {
                Medium.GAME -> GameCover(item.coverImageId, width = 160.dp)
                Medium.MOVIE -> GameCover(null, width = 160.dp, imageUrl = item.coverUrl)
                Medium.BOOK -> BookCover(url = BookImage.sized(item.coverUrl, 'M'), title = item.title, modifier = Modifier.width(140.dp))
            }
            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            item.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Glass.TextMuted) }
            Text(ratedLabel(item), style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted, textAlign = TextAlign.Center)
            if (item.medium == Medium.BOOK) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (1..item.maxRating).forEach { n ->
                        IconButton(onClick = { onRate(n) }, modifier = Modifier.size(44.dp)) {
                            val filled = n <= (item.rating ?: 0)
                            Icon(
                                if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = stringResource(R.string.book_rate_stars, n),
                                tint = if (filled) Glass.Amber else Glass.TextMuted,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..item.maxRating).forEach { n -> GlassPill(text = "$n", selected = item.rating == n, onClick = { onRate(n) }) }
                }
            }
        }
    }
    item.rating?.let { current ->
        GradientButton(
            text = stringResource(R.string.review_keep, current, item.maxRating),
            onClick = { onRate(current) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    GlassButton(text = stringResource(R.string.action_skip), onClick = onSkip, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun FinishedCard(rated: Int, total: Int, onRestart: () -> Unit, onBack: () -> Unit) {
    Text(stringResource(R.string.review_done_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(
        LocalContext.current.resources.getQuantityString(R.plurals.review_done_count, rated, rated, total),
        style = MaterialTheme.typography.bodyLarge,
        color = Glass.TextMuted,
        textAlign = TextAlign.Center,
    )
    GradientButton(stringResource(R.string.review_restart), onClick = onRestart, modifier = Modifier.fillMaxWidth())
    GlassButton(stringResource(R.string.action_back), onClick = onBack, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ratedLabel(item: ReviewItem): String = when {
    item.rating == null -> stringResource(R.string.review_never_rated)
    item.ratedAt == null -> stringResource(R.string.review_rated_unknown_date, item.rating, item.maxRating)
    else -> stringResource(R.string.review_rated_on, item.rating, item.maxRating, formatDate(item.ratedAt))
}

private fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH))
