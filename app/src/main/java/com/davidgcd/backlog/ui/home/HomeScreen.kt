package com.davidgcd.backlog.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.Medium
import com.davidgcd.backlog.model.TimeBudget
import com.davidgcd.backlog.model.Tonight
import com.davidgcd.backlog.model.TonightCandidate
import com.davidgcd.backlog.ui.components.BookCover
import com.davidgcd.backlog.ui.components.GameCover
import com.davidgcd.backlog.ui.components.GlassBadge
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.LibraryEmptyState
import com.davidgcd.backlog.ui.components.SectionHeader
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.prefs.LocalUiPreferences
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.BookImage
import com.davidgcd.backlog.util.ReleaseDateFormatting
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * « Aujourd'hui » : ce qui compte maintenant, tous médias confondus — une idée pour ce soir, ce qui est en cours,
 * les sorties de la quinzaine, les derniers ajouts, et le bilan de l'année. Les trois médias vivent ensuite dans la Bibliothèque.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpen: (Medium, String) -> Unit,
    onSearch: () -> Unit,
    onOpenRecap: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val nudge = rememberNudgeState()
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Glass.Text,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painter = painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.home_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text(todayLabel(), style = MaterialTheme.typography.labelMedium, color = Glass.TextMuted)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isEmpty) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                LibraryEmptyState(
                    message = stringResource(R.string.home_empty),
                    actionLabel = stringResource(R.string.home_empty_cta),
                    onAction = onSearch,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "tonight") { TonightCard(state.tonightPool, onOpen) }
            if (state.upcoming.isNotEmpty() && nudge.visible) item(key = "notif") { NotificationNudgeCard(nudge) }
            if (state.inProgress.isNotEmpty()) {
                item(key = "continue") {
                    Column {
                        SectionHeader(stringResource(R.string.home_continue))
                        Carousel(state.inProgress, onOpen)
                    }
                }
            }
            if (state.upcoming.isNotEmpty()) {
                item(key = "upcoming-header") { SectionHeader(stringResource(R.string.home_upcoming)) }
                items(state.upcoming, key = { "up-${it.item.medium}-${it.item.key}" }) { up -> UpcomingRow(up, onOpen) }
            }
            if (state.recent.isNotEmpty()) {
                item(key = "recent") {
                    Column {
                        SectionHeader(stringResource(R.string.section_recent))
                        Carousel(state.recent, onOpen)
                    }
                }
            }
            item(key = "recap") { RecapCard(onOpenRecap) }
        }
    }
}

@Composable
private fun todayLabel(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }

@Composable
private fun mediumLabel(medium: Medium): String = stringResource(
    when (medium) {
        Medium.GAME -> R.string.media_game_one
        Medium.MOVIE -> R.string.media_movie_one
        Medium.BOOK -> R.string.media_book_one
    },
)

@Composable
private fun ItemCover(medium: Medium, imageId: String?, url: String?, title: String, width: Dp, description: String? = null) {
    when (medium) {
        Medium.GAME -> GameCover(imageId, width = width, contentDescription = description)
        Medium.MOVIE -> GameCover(null, width = width, imageUrl = url, contentDescription = description)
        Medium.BOOK -> BookCover(url = BookImage.sized(url, 'M'), title = title, modifier = Modifier.width(width))
    }
}

@Composable
private fun Carousel(items: List<HomeItem>, onOpen: (Medium, String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
    ) {
        items(items, key = { "${it.medium}-${it.key}" }) { item ->
            Column(
                modifier = Modifier.width(104.dp).clickable { onOpen(item.medium, item.key) },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ItemCover(item.medium, item.coverImageId, item.coverUrl, item.title, width = 104.dp)
                Text(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Glass.Text)
            }
        }
    }
}

@Composable
private fun UpcomingRow(up: UpcomingItem, onOpen: (Medium, String) -> Unit) {
    val daysAway = (up.epochSeconds / 86_400L) - (System.currentTimeMillis() / 1000 / 86_400L)
    val relative = when {
        daysAway <= 0L -> stringResource(R.string.home_today)
        daysAway == 1L -> stringResource(R.string.home_tomorrow)
        else -> stringResource(R.string.home_in_days, daysAway.toInt())
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(up.item.medium, up.item.key) }) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ItemCover(up.item.medium, up.item.coverImageId, up.item.coverUrl, up.item.title, width = 48.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(up.item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ReleaseDateFormatting.format(up.epochSeconds).orEmpty(), style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GlassBadge(relative, tint = Glass.Cyan)
                GlassBadge(mediumLabel(up.item.medium))
            }
        }
    }
}

/** « Ce soir, je fais quoi ? » : trois idées tirées de la bibliothèque selon le média et le temps disponibles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TonightCard(pool: List<TonightCandidate>, onOpen: (Medium, String) -> Unit) {
    var medium by rememberSaveable { mutableStateOf<Medium?>(null) }
    var budget by rememberSaveable { mutableStateOf(TimeBudget.EVENING) }
    var seed by rememberSaveable { mutableStateOf(0L) }
    val picks = remember(pool, medium, budget, seed) { Tonight.suggest(pool, medium, budget, System.currentTimeMillis(), seed) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Glass.Cyan)
                Text(
                    stringResource(R.string.tonight_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { seed += 1 }, enabled = picks.isNotEmpty()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.tonight_another), modifier = Modifier.padding(start = 4.dp))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPill(stringResource(R.string.filter_any), selected = medium == null, onClick = { medium = null })
                GlassPill(stringResource(R.string.media_games), selected = medium == Medium.GAME, onClick = { medium = Medium.GAME })
                GlassPill(stringResource(R.string.media_movies), selected = medium == Medium.MOVIE, onClick = { medium = Medium.MOVIE })
                GlassPill(stringResource(R.string.media_books), selected = medium == Medium.BOOK, onClick = { medium = Medium.BOOK })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeBudget.entries.forEach { option ->
                    GlassPill(stringResource(option.labelRes()), selected = budget == option, onClick = { budget = option })
                }
            }
            if (picks.isEmpty()) {
                Text(stringResource(R.string.tonight_none), style = MaterialTheme.typography.bodyMedium, color = Glass.TextMuted)
            } else {
                picks.forEach { pick -> TonightRow(pick, onOpen) }
                if (budget != TimeBudget.LONG && medium != Medium.MOVIE && medium != Medium.BOOK) {
                    Text(stringResource(R.string.tonight_games_note), style = MaterialTheme.typography.labelSmall, color = Glass.TextMuted)
                }
            }
        }
    }
}

private fun TimeBudget.labelRes(): Int = when (this) {
    TimeBudget.SHORT -> R.string.tonight_short
    TimeBudget.EVENING -> R.string.tonight_evening
    TimeBudget.LONG -> R.string.tonight_long
}

@Composable
private fun TonightRow(pick: TonightCandidate, onOpen: (Medium, String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(pick.medium, pick.key) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ItemCover(pick.medium, pick.coverImageId, pick.coverUrl, pick.title, width = 48.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(pick.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassBadge(mediumLabel(pick.medium))
                pick.durationMinutes?.let { GlassBadge(formatDuration(it)) }
                if (pick.inProgress) GlassBadge(stringResource(R.string.tonight_in_progress), tint = Glass.Amber)
            }
        }
    }
}

private fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${"%02d".format(minutes % 60)}"
}

/** État de la carte « Être prévenu des sorties ? » : visible tant que l'autorisation manque et n'a pas déjà été demandée. */
private class NudgeState(val visible: Boolean, val enable: () -> Unit, val later: () -> Unit)

/**
 * Demande la permission de notifications au bon moment : quand on affiche des sorties à venir, pas au lancement.
 * Une seule fois ; refusée, elle ne revient pas (les Réglages permettent de l'activer ensuite).
 */
@Composable
private fun rememberNudgeState(): NudgeState {
    val prefs = LocalUiPreferences.current
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var asked by remember { mutableStateOf(prefs?.notificationPermissionAsked ?: true) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    return NudgeState(
        visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && prefs != null && !granted && !asked,
        enable = {
            prefs?.notificationPermissionAsked = true
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        later = {
            prefs?.notificationPermissionAsked = true
            asked = true
        },
    )
}

@Composable
private fun NotificationNudgeCard(nudge: NudgeState) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.notif_nudge_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.notif_nudge_message), style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = nudge.later) { Text(stringResource(R.string.notif_nudge_later)) }
                TextButton(onClick = nudge.enable) { Text(stringResource(R.string.notif_nudge_enable)) }
            }
        }
    }
}

fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
private fun RecapCard(onOpenRecap: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenRecap) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Filled.Timeline, contentDescription = null, tint = Glass.Cyan, modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.recap_card_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.recap_card_subtitle), style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted)
            }
        }
    }
}
