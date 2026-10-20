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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.Medium
import com.davidgcd.backlog.ui.components.BookCover
import com.davidgcd.backlog.ui.components.GameCover
import com.davidgcd.backlog.ui.components.GlassBadge
import com.davidgcd.backlog.ui.components.GlassCard
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
 * « Aujourd'hui » : ce qui compte maintenant, tous médias confondus — ce qui est en cours,
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
            if (state.upcoming.isNotEmpty() && nudge.visible) item(key = "notif") { NotificationNudgeCard(nudge) }
            item(key = "summary") { SummaryCards(state) }
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
private fun ItemCover(medium: Medium, imageId: String?, url: String?, title: String, width: androidx.compose.ui.unit.Dp, description: String? = null) {
    when (medium) {
        Medium.GAME -> GameCover(imageId, width = width, contentDescription = description)
        Medium.MOVIE -> GameCover(null, width = width, imageUrl = url, contentDescription = description)
        Medium.BOOK -> BookCover(url = BookImage.sized(url, 'M'), title = title, modifier = Modifier.width(width))
    }
}

@Composable
private fun SummaryCards(state: HomeState) {
    val inProgressCount = state.inProgress.size
    val nowSeconds = System.currentTimeMillis() / 1000
    val upcomingCount = state.upcoming.count { it.epochSeconds <= nowSeconds + 7 * 86_400 }
    val recentCount = state.recent.size

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard(
            title = stringResource(R.string.home_summary_in_progress),
            value = inProgressCount.toString(),
            icon = Icons.Filled.PlayArrow,
            color = Glass.Cyan
        )
        SummaryCard(
            title = stringResource(R.string.home_summary_upcoming),
            value = upcomingCount.toString(),
            icon = Icons.Filled.CalendarToday,
            color = Glass.Amber
        )
        SummaryCard(
            title = stringResource(R.string.home_summary_recent),
            value = recentCount.toString(),
            icon = Icons.Filled.NewReleases,
            color = Glass.Green
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(title, style = MaterialTheme.typography.labelSmall, color = Glass.TextMuted)
                }
            }
        }
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val mediumIcon = when (item.medium) {
                    Medium.GAME -> Icons.Filled.Games
                    Medium.MOVIE -> Icons.Filled.LocalMovies
                    Medium.BOOK -> Icons.Filled.MenuBook
                }
                Icon(
                    mediumIcon,
                    contentDescription = null,
                    tint = when (item.medium) {
                        Medium.GAME -> Glass.Cyan
                        Medium.MOVIE -> Glass.Purple
                        Medium.BOOK -> Glass.Green
                    },
                    modifier = Modifier.size(16.dp)
                )
                ItemCover(item.medium, item.coverImageId, item.coverUrl, item.title, width = 104.dp)
                Text(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Glass.Text)
            }
        }
    }
}

@Composable
private fun UpcomingRow(up: UpcomingItem, onOpen: (Medium, String) -> Unit) {
    val nowSeconds = System.currentTimeMillis() / 1000
    val daysAway = (up.epochSeconds - nowSeconds) / 86_400
    val (label, color) = when {
        daysAway <= 0L -> Pair(stringResource(R.string.home_today), Glass.Pink)
        daysAway == 1L -> Pair(stringResource(R.string.home_tomorrow), Glass.Amber)
        daysAway <= 7L -> Pair(stringResource(R.string.home_in_days, daysAway.toInt()), Glass.Green)
        else -> Pair(stringResource(R.string.home_in_days, daysAway.toInt()), Glass.Cyan)
    }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(up.item.medium, up.item.key) },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val mediumIcon = when (up.item.medium) {
                Medium.GAME -> Icons.Filled.Games
                Medium.MOVIE -> Icons.Filled.LocalMovies
                Medium.BOOK -> Icons.Filled.MenuBook
            }
            Icon(
                mediumIcon,
                contentDescription = null,
                tint = when (up.item.medium) {
                    Medium.GAME -> Glass.Cyan
                    Medium.MOVIE -> Glass.Purple
                    Medium.BOOK -> Glass.Green
                },
                modifier = Modifier.size(20.dp)
            )
            ItemCover(up.item.medium, up.item.coverImageId, up.item.coverUrl, up.item.title, width = 48.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(up.item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ReleaseDateFormatting.format(up.epochSeconds).orEmpty(), style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted)
            }
            GlassBadge(label, tint = color)
        }
    }
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
