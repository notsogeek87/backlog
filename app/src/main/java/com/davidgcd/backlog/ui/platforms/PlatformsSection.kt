package com.davidgcd.backlog.ui.platforms

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.library.LibraryAccount
import com.davidgcd.backlog.ui.components.GlassBadge
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.theme.Glass

/** Settings block: one card per store. Only Steam exists today; add a card here per future provider. */
@Composable
fun PlatformsSection(
    viewModel: PlatformsViewModel,
    onConnectSteam: () -> Unit,
    onSyncSteam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steam by viewModel.steam.collectAsState()
    var confirmDisconnect by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.platforms_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        SteamCard(steam, onConnectSteam, onSyncSteam, onDisconnect = { confirmDisconnect = true })
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.platform_disconnect_title)) },
            text = { Text(stringResource(R.string.platform_disconnect_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    viewModel.disconnectSteam()
                }) { Text(stringResource(R.string.platform_disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SteamCard(
    account: LibraryAccount?,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = Glass.Cyan, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.platform_steam), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Glass.Text)
                    Text(
                        text = when {
                            account == null -> stringResource(R.string.platform_not_connected)
                            account.displayName != null -> stringResource(R.string.platform_connected_as, account.displayName)
                            else -> stringResource(R.string.platform_connected)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.TextMuted,
                    )
                }
                if (account != null) GlassBadge(stringResource(R.string.platform_connected), tint = Glass.Green)
            }

            if (account == null) {
                GradientButton(
                    text = stringResource(R.string.platform_connect_steam),
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SteamStats(account)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GradientButton(stringResource(R.string.platform_sync), onSync, Modifier.weight(1f))
                    GlassButton(stringResource(R.string.platform_disconnect), onDisconnect, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SteamStats(account: LibraryAccount) {
    val owned = account.ownedCount
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (owned != null) {
            Text(
                pluralText(R.plurals.platform_games_detected, owned, owned),
                style = MaterialTheme.typography.bodyMedium,
                color = Glass.Text,
            )
            account.alreadyInBacklogCount?.let {
                Text(stringResource(R.string.platform_stats_present, it), style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted)
            }
            account.newCount?.let {
                Text(stringResource(R.string.platform_stats_new, it), style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted)
            }
        }
        val last = account.lastSyncedAt
        Text(
            text = if (last == null) {
                stringResource(R.string.platform_never_synced)
            } else {
                // "5 minutes ago" for recent syncs; falls back to a date once it's more than a week old.
                stringResource(
                    R.string.platform_last_sync,
                    DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = Glass.TextMuted,
        )
    }
}
