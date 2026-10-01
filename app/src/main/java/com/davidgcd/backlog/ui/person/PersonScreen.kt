package com.davidgcd.backlog.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.davidgcd.backlog.R
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.movies.MediaTitleListItem
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.TmdbImage
import kotlinx.coroutines.launch

/** An actor's or director's page: their photo and every film & série they played in / directed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    viewModel: PersonViewModel,
    onBack: () -> Unit,
    onTitleClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                is PersonState.Loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator(color = Glass.Cyan) }

                is PersonState.Error -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.person_error),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    GlassButton(
                        text = stringResource(R.string.action_retry),
                        onClick = viewModel::refresh,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                is PersonState.Loaded -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val photoModifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (viewModel.asDirector) Glass.Cyan else Glass.Border, CircleShape)
                            val photo = TmdbImage.profile(current.person.photoPath)
                            if (photo != null) {
                                AsyncImage(model = photo, contentDescription = current.person.name, contentScale = ContentScale.Crop, modifier = photoModifier)
                            } else {
                                androidx.compose.foundation.layout.Box(photoModifier, contentAlignment = Alignment.Center) {
                                    Text(current.person.name.first().uppercase(), style = MaterialTheme.typography.titleLarge, color = Glass.TextMuted)
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(current.person.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Glass.Text)
                                Text(
                                    stringResource(if (viewModel.asDirector) R.string.person_directed else R.string.person_acted_in),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (viewModel.asDirector) Glass.Cyan else Glass.TextMuted,
                                )
                            }
                        }
                    }
                    if (current.person.titles.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(stringResource(R.string.person_empty), color = Glass.TextMuted, modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                    items(current.person.titles, key = { it.id }) { title ->
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
                            onClick = { onTitleClick(title.id) },
                        )
                    }
                }
            }
        }
    }
}
