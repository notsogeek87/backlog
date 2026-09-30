package com.davidgcd.backlog.ui.platforms

import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.library.LibraryAccount
import com.davidgcd.backlog.data.library.LibraryProviders
import com.davidgcd.backlog.data.library.android.PackageManagerInstalledApps
import com.davidgcd.backlog.ui.components.GlassBadge
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.theme.Glass

/** Settings block: one card per source (Steam, this phone). Add a card here per future provider. */
@Composable
fun PlatformsSection(
    viewModel: PlatformsViewModel,
    onConnectSteam: () -> Unit,
    onSyncSteam: () -> Unit,
    onSyncAndroid: () -> Unit,
    onConnectTmdb: () -> Unit,
    onSyncTmdb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steam by viewModel.steam.collectAsState()
    val tmdb by viewModel.tmdb.collectAsState()
    val android by viewModel.android.collectAsState()
    var confirmDisconnect by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.platforms_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        SteamCard(steam, onConnectSteam, onSyncSteam, onDisconnect = { confirmDisconnect = LibraryProviders.STEAM })
        AndroidCard(
            account = android,
            onConnect = { viewModel.connectAndroid(onReady = onSyncAndroid) },
            onSync = onSyncAndroid,
            onDisconnect = { confirmDisconnect = LibraryProviders.ANDROID },
        )
        TmdbCard(tmdb, onConnectTmdb, onSyncTmdb, onDisconnect = { confirmDisconnect = LibraryProviders.TMDB })
    }

    confirmDisconnect?.let { providerId ->
        val isAndroid = providerId == LibraryProviders.ANDROID
        val isTmdb = providerId == LibraryProviders.TMDB
        AlertDialog(
            onDismissRequest = { confirmDisconnect = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(if (isAndroid) R.string.platform_android_disconnect_title else if (isTmdb) R.string.platform_tmdb_disconnect_title else R.string.platform_disconnect_title)) },
            text = { Text(stringResource(if (isAndroid) R.string.platform_android_disconnect_message else if (isTmdb) R.string.platform_tmdb_disconnect_message else R.string.platform_disconnect_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = null
                    viewModel.disconnect(providerId)
                }) { Text(stringResource(R.string.platform_disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = null }) { Text(stringResource(R.string.action_cancel)) }
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
                LibraryStats(account)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GradientButton(stringResource(R.string.platform_sync), onSync, Modifier.weight(1f))
                    GlassButton(stringResource(R.string.platform_disconnect), onDisconnect, Modifier.weight(1f))
                }
            }
        }
    }
}

/** TMDB account: films & séries (watchlist + ratings). Connect opens the TMDB sign-in page. */
@Composable
private fun TmdbCard(
    account: LibraryAccount?,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Movie, contentDescription = null, tint = Glass.Amber, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.platform_tmdb), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Glass.Text)
                    Text(
                        text = if (account == null) stringResource(R.string.platform_tmdb_hint) else stringResource(R.string.platform_tmdb_connected_as, account.displayName ?: account.accountId),
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.TextMuted,
                    )
                }
                if (account != null) GlassBadge(stringResource(R.string.platform_connected), tint = Glass.Green)
            }
            Text(stringResource(R.string.tmdb_attribution), style = MaterialTheme.typography.labelSmall, color = Glass.TextMuted)

            if (account == null) {
                GradientButton(
                    text = stringResource(R.string.platform_connect_tmdb),
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                account.ownedCount?.let {
                    Text(pluralText(R.plurals.platform_tmdb_titles, it, it), style = MaterialTheme.typography.bodyMedium, color = Glass.Text)
                }
                Text(
                    text = account.lastSyncedAt?.let { last ->
                        stringResource(
                            R.string.platform_last_sync,
                            DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                        )
                    } ?: stringResource(R.string.platform_never_synced),
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.TextMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GradientButton(stringResource(R.string.platform_sync), onSync, Modifier.weight(1f))
                    GlassButton(stringResource(R.string.platform_disconnect), onDisconnect, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LibraryStats(account: LibraryAccount) {
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

/** Games installed on this phone. No login; optionally reads usage stats (special access) for time played. */
@Composable
private fun AndroidCard(
    account: LibraryAccount?,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    // The grant happens in system settings: re-check every time the user comes back to the app.
    var usageAccess by remember { mutableStateOf(PackageManagerInstalledApps.hasUsageAccess(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) usageAccess = PackageManagerInstalledApps.hasUsageAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = Glass.Green, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.platform_android), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Glass.Text)
                    Text(
                        text = if (account == null) stringResource(R.string.platform_android_hint) else account.displayName ?: stringResource(R.string.platform_connected),
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.TextMuted,
                    )
                }
                if (account != null) GlassBadge(stringResource(R.string.platform_connected), tint = Glass.Green)
            }

            if (account == null) {
                GradientButton(
                    text = stringResource(R.string.platform_android_scan),
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LibraryStats(account)
                Text(
                    text = stringResource(if (usageAccess) R.string.platform_android_usage_on else R.string.platform_android_usage_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.TextMuted,
                )
                if (!usageAccess) {
                    GlassButton(
                        text = stringResource(R.string.platform_android_usage_grant),
                        onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GradientButton(stringResource(R.string.platform_sync), onSync, Modifier.weight(1f))
                    GlassButton(stringResource(R.string.platform_disconnect), onDisconnect, Modifier.weight(1f))
                }
            }
        }
    }
}
