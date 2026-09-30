package com.davidgcd.backlog.ui.moviedetail

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.local.watchStatus
import com.davidgcd.backlog.data.repository.toMediaTitle
import com.davidgcd.backlog.model.MediaTitle
import com.davidgcd.backlog.model.WatchStatus
import com.davidgcd.backlog.ui.components.GameCover
import com.davidgcd.backlog.ui.components.GlassBadge
import com.davidgcd.backlog.ui.components.GlassBadgeButton
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.components.GradientProgressBar
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.components.label
import com.davidgcd.backlog.ui.components.tint
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.TmdbImage
import com.davidgcd.backlog.util.ReleaseDateFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(viewModel: MovieDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    val backdrop = when (val s = state) {
        is MovieDetailState.Saved -> s.movie.posterUrl
        is MovieDetailState.Remote -> s.title.posterUrl
        else -> null
    }?.let { TmdbImage.poster(it) }

    Box(modifier = Modifier.fillMaxSize()) {
        // The poster tints the screen: blurred, dimmed, faded into the night canvas (blur needs API 31+).
        if (backdrop != null) {
            Box(modifier = Modifier.fillMaxWidth().height(460.dp).clipToBounds()) {
                AsyncImage(
                    model = backdrop,
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
                    is MovieDetailState.Loading -> Centered { CircularProgressIndicator(color = Glass.Cyan) }
                    is MovieDetailState.NotFound -> Centered { Text(stringResource(R.string.movie_not_found)) }
                    is MovieDetailState.Saved -> MovieContent(
                        title = current.movie.toMediaTitle(),
                        saved = current.movie,
                        onStatusChange = { viewModel.setStatus(current.movie, it) },
                        onRate = { viewModel.setUserRating(current.movie, it) },
                        onArchiveToggle = { viewModel.setArchived(current.movie, !current.movie.isArchived) },
                        onRemove = { viewModel.remove(current.movie, onDone = onBack) },
                        onAdd = {},
                    )
                    is MovieDetailState.Remote -> MovieContent(
                        title = current.title,
                        saved = null,
                        onStatusChange = {},
                        onRate = {},
                        onArchiveToggle = {},
                        onRemove = {},
                        onAdd = { viewModel.add(current.title) },
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
private fun MovieContent(
    title: MediaTitle,
    saved: MovieEntity?,
    onStatusChange: (WatchStatus) -> Unit,
    onRate: (Int) -> Unit,
    onArchiveToggle: () -> Unit,
    onRemove: () -> Unit,
    onAdd: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                GameCover(imageId = null, width = 132.dp, imageUrl = TmdbImage.poster(title.posterUrl))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = title.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Glass.Text,
                    )
                    val facts = listOfNotNull(
                        title.kind.label(),
                        (ReleaseDateFormatting.format(title.releaseDate) ?: title.year?.toString()),
                        title.runtimeMinutes?.let { stringResource(R.string.movie_runtime_minutes, it) },
                    ).joinToString(" · ")
                    Text(facts, style = MaterialTheme.typography.bodyMedium, color = Glass.TextMuted)
                    if (saved?.isArchived == true) GlassBadge(stringResource(R.string.label_archived), tint = Glass.Amber)
                }
            }

            if (title.genres.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    title.genres.forEach { GlassBadge(it, tint = Glass.Purple) }
                }
            }

            if (saved != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.status_title), style = MaterialTheme.typography.labelLarge, color = Glass.TextMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WatchStatus.entries.forEach { option ->
                            GlassBadgeButton(option.label(), option.tint(), selected = option == saved.watchStatus) { onStatusChange(option) }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.movie_my_rating), style = MaterialTheme.typography.labelLarge, color = Glass.TextMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..10).forEach { n -> GlassPill(text = "$n", selected = saved.userRating == n, onClick = { onRate(n) }) }
                    }
                }
            }

            title.rating?.let { rating ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.ratings_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ratings_tmdb), style = MaterialTheme.typography.labelLarge, color = Glass.TextMuted, modifier = Modifier.weight(1f))
                            Text("%.1f / 10".format(java.util.Locale.FRENCH, rating), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        GradientProgressBar((rating / 10.0).toFloat())
                    }
                }
            }

            listOfNotNull(
                title.directors?.let { stringResource(R.string.movie_directors, it) },
                title.cast?.let { stringResource(R.string.movie_cast, it) },
            ).takeIf { it.isNotEmpty() }?.let { lines ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Glass.TextMuted) }
                }
            }

            title.plot?.let { plot ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Glass.Text.copy(alpha = 0.86f),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Column(modifier = Modifier.padding(top = 8.dp, bottom = 24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (saved != null) {
                    GlassButton(
                        text = stringResource(if (saved.isArchived) R.string.action_unarchive else R.string.action_archive),
                        onClick = onArchiveToggle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    GradientButton(
                        text = stringResource(R.string.action_add_to_movies),
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                GlassButton(
                    text = stringResource(R.string.action_open_tmdb),
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(title.tmdbUrl))) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (saved != null) {
                    GlassButton(
                        text = stringResource(R.string.action_remove_from_movies),
                        onClick = { confirmRemove = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentColor = MaterialTheme.colorScheme.error,
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
            text = { Text(stringResource(R.string.remove_movie_confirm_message, title.title)) },
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
