package com.davidgcd.backlog.ui.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.util.IgdbImage
import com.davidgcd.backlog.util.ReleaseDateFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(viewModel: GameDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                is GameDetailState.Loading -> Centered { CircularProgressIndicator() }
                is GameDetailState.NotFound -> Centered { Text("Game not found") }
                is GameDetailState.InBacklog -> GameDetailContent(
                    display = current.entity.toDisplay(),
                    inBacklog = true,
                    isArchived = current.entity.isArchived,
                    onArchiveToggle = { viewModel.setArchived(current.entity, !current.entity.isArchived) },
                    onRemove = { viewModel.remove(current.entity) },
                    onAdd = {},
                )
                is GameDetailState.Remote -> GameDetailContent(
                    display = current.game.toDisplay(),
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
    inBacklog: Boolean,
    isArchived: Boolean,
    onArchiveToggle: () -> Unit,
    onRemove: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        display.coverImageId?.let { imageId ->
            AsyncImage(
                model = IgdbImage.url(imageId, IgdbImage.Size.CoverBig),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
            )
        }

        Text(
            text = display.name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp),
        )

        ReleaseDateFormatting.format(display.firstReleaseDate)?.let { date ->
            Text(text = date, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }

        if (display.genres.isNotEmpty()) {
            Text(
                text = display.genres.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        display.summary?.let { summary ->
            Text(text = summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
        }

        if (display.platforms.isNotEmpty()) {
            Text(
                text = "Platforms: " + display.platforms.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Column(modifier = Modifier.padding(top = 24.dp)) {
            if (inBacklog) {
                Button(onClick = onArchiveToggle) {
                    Text(if (isArchived) "Unarchive" else "Archive")
                }
                Button(onClick = onRemove, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Remove from backlog")
                }
            } else {
                Button(onClick = onAdd) {
                    Text("Add to backlog")
                }
            }
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
    genres = emptyList(), // decoded from genresJson once a JSON-cache reader lands, see README
    platforms = emptyList(),
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
