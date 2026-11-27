package com.davidgcd.backlog.ui.notifications

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.Medium
import com.davidgcd.backlog.notifications.NotificationEntry
import com.davidgcd.backlog.notifications.NotificationKind
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass

/**
 * « Notifications » : toutes les alertes envoyées (sorties, promotions, épisodes, dates, plateformes), y compris
 * celles effacées du volet système. Un appui ouvre la fiche concernée.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    viewModel: NotificationHistoryViewModel,
    onOpen: (Medium, String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    // Ce qui était nouveau à l'ouverture reste mis en avant pendant la visite ; la pastille retombe en quittant.
    DisposableEffect(Unit) { onDispose { viewModel.markAllSeen() } }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Glass.Text,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = { Text(stringResource(R.string.notification_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.entries.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.notification_history_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.entries.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.NotificationsNone, contentDescription = null, tint = Glass.TextMuted, modifier = Modifier.size(48.dp))
                    Text(
                        stringResource(R.string.notification_history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Glass.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.entries, key = { it.id }) { entry ->
                    EntryRow(
                        entry = entry,
                        isNew = entry.postedAtMillis > viewModel.newAfterMillis,
                        onClick = { onOpen(entry.medium, entry.itemKey) },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.notification_history_clear_title)) },
            text = { Text(stringResource(R.string.notification_history_clear_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clear()
                }) { Text(stringResource(R.string.notification_history_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun EntryRow(entry: NotificationEntry, isNew: Boolean, onClick: () -> Unit) {
    val (icon, tint) = kindStyle(entry.kind)
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = stringResource(kindLabel(entry.kind)), tint = tint, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isNew) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(entry.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    DateUtils.getRelativeTimeSpanString(entry.postedAtMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isNew) Glass.Cyan else Glass.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun kindStyle(kind: NotificationKind): Pair<ImageVector, Color> = when (kind) {
    NotificationKind.RELEASE -> Icons.Filled.NewReleases to Glass.Cyan
    NotificationKind.DATE_CHANGE -> Icons.Filled.Event to Glass.Amber
    NotificationKind.NEW_PLATFORM -> Icons.Filled.Devices to Glass.Cyan
    NotificationKind.DEAL -> Icons.Filled.LocalOffer to Glass.Green
    NotificationKind.EPISODE -> Icons.Filled.FiberNew to Glass.Pink
}

private fun kindLabel(kind: NotificationKind): Int = when (kind) {
    NotificationKind.RELEASE -> R.string.notification_channel_release
    NotificationKind.DATE_CHANGE, NotificationKind.NEW_PLATFORM -> R.string.notification_channel_drift
    NotificationKind.DEAL -> R.string.notification_channel_deal
    NotificationKind.EPISODE -> R.string.notification_channel_episode
}
