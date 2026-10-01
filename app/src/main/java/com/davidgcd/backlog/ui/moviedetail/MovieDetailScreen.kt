package com.davidgcd.backlog.ui.moviedetail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.davidgcd.backlog.model.CastMember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import com.davidgcd.backlog.model.TitleKind
import com.davidgcd.backlog.model.WatchProvider
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
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
fun MovieDetailScreen(viewModel: MovieDetailViewModel, onBack: () -> Unit, onPersonClick: (personId: Long, director: Boolean) -> Unit) {
    val state by viewModel.state.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val credits by viewModel.credits.collectAsState()

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
                        providers = providers,
                        credits = credits,
                        title = current.movie.toMediaTitle(),
                        saved = current.movie,
                        onStatusChange = { viewModel.setStatus(current.movie, it) },
                        onRate = { viewModel.setUserRating(current.movie, it) },
                        onArchiveToggle = { viewModel.setArchived(current.movie, !current.movie.isArchived) },
                        onRemove = { viewModel.remove(current.movie, onDone = onBack) },
                        onAdd = {},
                        onPersonClick = onPersonClick,
                    )
                    is MovieDetailState.Remote -> MovieContent(
                        providers = providers,
                        credits = credits,
                        title = current.title,
                        saved = null,
                        onStatusChange = {},
                        onRate = {},
                        onArchiveToggle = {},
                        onRemove = {},
                        onAdd = { viewModel.add(current.title) },
                        onPersonClick = onPersonClick,
                    )
                }
            }
        }
    }
}

/** The director (creator for a series) then the top-billed actors, each with their photo, in a swipeable row. */
@Composable
private fun CastRow(people: List<CastMember>, kind: TitleKind, onPersonClick: (Long, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.cast_title), style = MaterialTheme.typography.labelLarge, color = Glass.TextMuted)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            people.forEach { person ->
                Column(
                    modifier = Modifier
                        .width(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            person.personId?.let { id ->
                                Modifier.clickable { onPersonClick(id, person.isDirector) }
                            } ?: Modifier,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val photo = TmdbImage.profile(person.photoPath)
                    val photoModifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, if (person.isDirector) Glass.Cyan else Glass.Border, CircleShape)
                    if (photo != null) {
                        AsyncImage(model = photo, contentDescription = person.name, contentScale = ContentScale.Crop, modifier = photoModifier)
                    } else {
                        Box(photoModifier, contentAlignment = Alignment.Center) {
                            Text(person.name.first().uppercase(), style = MaterialTheme.typography.titleMedium, color = Glass.TextMuted)
                        }
                    }
                    Text(
                        person.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Glass.Text,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    val role = if (person.isDirector) {
                        stringResource(if (kind == TitleKind.SERIES) R.string.credit_creator else R.string.credit_director)
                    } else {
                        person.role
                    }
                    role?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (person.isDirector) Glass.Cyan else Glass.TextMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
    providers: ProvidersState,
    credits: List<CastMember>,
    title: MediaTitle,
    saved: MovieEntity?,
    onStatusChange: (WatchStatus) -> Unit,
    onRate: (Int) -> Unit,
    onArchiveToggle: () -> Unit,
    onRemove: () -> Unit,
    onAdd: () -> Unit,
    onPersonClick: (Long, Boolean) -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    var askRating by remember { mutableStateOf(false) }
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
                            GlassBadgeButton(option.label(), option.tint(), selected = option == saved.watchStatus) {
                                // Marking something seen is the moment to note it (skippable, only while unrated).
                                if (option == WatchStatus.WATCHED && saved.watchStatus != WatchStatus.WATCHED && saved.userRating == null) askRating = true
                                onStatusChange(option)
                            }
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

            if (credits.isNotEmpty()) {
                CastRow(credits, title.kind, onPersonClick)
            } else {
                listOfNotNull(
                    title.directors?.let { stringResource(if (title.kind == TitleKind.SERIES) R.string.series_creators else R.string.movie_directors, it) },
                    title.cast?.let { stringResource(R.string.movie_cast, it) },
                ).takeIf { it.isNotEmpty() }?.let { lines ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Glass.TextMuted) }
                    }
                }
            }

            WhereToWatchCard(providers)

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

    if (askRating) {
        AlertDialog(
            onDismissRequest = { askRating = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.movie_rate_prompt_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.movie_rate_prompt_message, title.title))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..10).forEach { n ->
                            GlassPill(text = "$n", selected = false, onClick = {
                                askRating = false
                                onRate(n)
                            })
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { askRating = false }) { Text(stringResource(R.string.action_skip)) }
            },
        )
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

/**
 * "Où regarder ?" — the platforms offering the title in France, one labelled row per way of watching
 * (subscription / rent / buy / free) so they are never confused. Hidden if TMDB can't be reached.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhereToWatchCard(state: ProvidersState) {
    if (state is ProvidersState.Unavailable) return
    val context = LocalContext.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.where_to_watch_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (state) {
                is ProvidersState.Loading -> CircularProgressIndicator(color = Glass.Cyan, modifier = Modifier.height(20.dp))
                is ProvidersState.Loaded -> {
                    val providers = state.providers
                    if (providers == null) {
                        Text(stringResource(R.string.where_to_watch_none), style = MaterialTheme.typography.bodyMedium, color = Glass.TextMuted)
                    } else {
                        ProviderRow(stringResource(R.string.where_to_watch_subscription), Glass.Cyan, providers.subscription)
                        ProviderRow(stringResource(R.string.where_to_watch_rent), Glass.Amber, providers.rent)
                        ProviderRow(stringResource(R.string.where_to_watch_buy), Glass.Purple, providers.buy)
                        ProviderRow(stringResource(R.string.where_to_watch_free), Glass.Green, providers.free)
                        providers.link?.let { link ->
                            GlassButton(
                                text = stringResource(R.string.where_to_watch_all_offers),
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                ProvidersState.Unavailable -> Unit
            }
            Text(stringResource(R.string.where_to_watch_credit), style = MaterialTheme.typography.labelSmall, color = Glass.TextMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderRow(label: String, tint: Color, providers: List<WatchProvider>) {
    if (providers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassBadge(label, tint = tint)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            providers.forEach { provider ->
                val shape = RoundedCornerShape(10.dp)
                val modifier = Modifier.size(48.dp).clip(shape).border(1.dp, Glass.Border, shape)
                if (provider.logoUrl != null) {
                    AsyncImage(
                        model = provider.logoUrl,
                        contentDescription = provider.name,
                        contentScale = ContentScale.Crop,
                        modifier = modifier,
                    )
                } else {
                    // No logo from TMDB: the name stays readable in a tile of the same size.
                    Box(modifier = modifier.background(Glass.GlassTop), contentAlignment = Alignment.Center) {
                        Text(provider.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(2.dp))
                    }
                }
            }
        }
    }
}
