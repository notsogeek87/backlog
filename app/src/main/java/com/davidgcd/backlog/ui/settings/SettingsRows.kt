package com.davidgcd.backlog.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.davidgcd.backlog.ui.prefs.ThemeMode
import com.davidgcd.backlog.ui.prefs.LocalUiPreferences
import com.davidgcd.backlog.ui.home.hasNotificationPermission
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.BuildConfig
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.share.ShareLinkService
import com.davidgcd.backlog.data.csv.AutoExportFrequency
import com.davidgcd.backlog.data.csv.AutoExportStatus
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.notifications.ReleaseReminderSchedule
import com.davidgcd.backlog.util.DebugLog

// Lignes, cartes et dialogues de l'écran Réglages (SettingsScreen.kt garde l'écran lui-même).

@Composable
internal fun ShareNameRow(saved: String, onSave: (String) -> Unit) {
    var text by remember(saved) { mutableStateOf(saved) }
    // Saved once the typing pauses, not on every keystroke.
    LaunchedEffect(text) {
        if (text != saved) {
            kotlinx.coroutines.delay(600)
            onSave(text)
        }
    }
    androidx.compose.material3.OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(ShareLinkService.MAX_OWNER_LENGTH) },
        label = { Text(stringResource(R.string.settings_share_name_title)) },
        supportingText = { Text(stringResource(R.string.settings_share_name_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}

@Composable
internal fun SectionTitle(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = topPadding),
    )
}

@Composable
internal fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // The whole row is the touch target (and one TalkBack node), not just the small switch.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleRow(current: ReleaseReminderSchedule, onSelect: (ReleaseReminderSchedule) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_remind_me), modifier = Modifier.weight(1f))
        TextButton(onClick = { expanded = true }) {
            Text(current.label())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReleaseReminderSchedule.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun ReleaseReminderSchedule.label(): String = stringResource(
    when (this) {
        ReleaseReminderSchedule.RELEASE_DAY -> R.string.schedule_release_day
        ReleaseReminderSchedule.DAY_BEFORE -> R.string.schedule_day_before
        ReleaseReminderSchedule.WEEK_BEFORE -> R.string.schedule_week_before
    },
)

@Composable
internal fun AutoExportFolderRow(folderName: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_auto_export_folder), modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) {
            Text(folderName ?: stringResource(R.string.settings_auto_export_folder_unavailable))
        }
    }
}

@Composable
internal fun AutoExportFrequencyRow(current: AutoExportFrequency, onSelect: (AutoExportFrequency) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_auto_export_frequency), modifier = Modifier.weight(1f))
        TextButton(onClick = { expanded = true }) { Text(current.label()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AutoExportFrequency.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun AutoExportFrequency.label(): String = stringResource(
    when (this) {
        AutoExportFrequency.DAILY -> R.string.settings_auto_export_daily
        AutoExportFrequency.WEEKLY -> R.string.settings_auto_export_weekly
        AutoExportFrequency.MONTHLY -> R.string.settings_auto_export_monthly
    },
)

@Composable
internal fun AutoExportStatusText(status: AutoExportStatus?) {
    val text = when {
        status == null -> stringResource(R.string.settings_auto_export_pending)
        else -> {
            val whenText = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                .format(java.util.Date(status.at))
            stringResource(
                if (status.succeeded) R.string.settings_auto_export_last_ok else R.string.settings_auto_export_last_failed,
                whenText,
            )
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (status?.succeeded == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    )
}

@Composable
internal fun TimeRow(hour: Int, minute: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_time), modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) {
            Text("%02d:%02d".format(hour, minute))
        }
    }
}

@Composable
internal fun DebugLogDialog(onDismiss: () -> Unit) {
    val entries by DebugLog.entries.collectAsState()
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
      Surface(modifier = Modifier.fillMaxSize(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_view_debug_log),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { DebugLog.clear() }) { Text(stringResource(R.string.action_clear)) }
                TextButton(onClick = { copyToClipboard(context, entries.joinToString("\n")) }) {
                    Text(stringResource(R.string.action_copy))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (entries.isEmpty()) {
                        Text(stringResource(R.string.settings_debug_log_empty))
                    } else {
                        entries.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
      }
    }
}

internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Backlog debug log", text))
}


/** Apparence : thème (système, sombre, clair, AMOLED), couleurs dynamiques (Android 12+), aperçu animé des bandes-annonces. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceCard() {
    val prefs = LocalUiPreferences.current ?: return
    val mode by prefs.themeMode.collectAsState()
    val dynamic by prefs.dynamicColors.collectAsState()
    val preview by prefs.trailerPreview.collectAsState()
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { option ->
                        GlassPill(stringResource(option.labelRes()), selected = mode == option, onClick = { prefs.setThemeMode(option) })
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HorizontalDivider(color = Glass.Border)
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_colors_title),
                    subtitle = stringResource(R.string.settings_dynamic_colors_subtitle),
                    checked = dynamic,
                    onCheckedChange = prefs::setDynamicColors,
                )
            }
            HorizontalDivider(color = Glass.Border)
            SettingsSwitchRow(
                title = stringResource(R.string.settings_trailer_preview_title),
                subtitle = stringResource(R.string.settings_trailer_preview_subtitle),
                checked = preview,
                onCheckedChange = prefs::setTrailerPreview,
            )
        }
    }
}

internal fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.AMOLED -> R.string.theme_amoled
}

/**
 * Les alertes ne partent que si Android autorise les notifications : quand une alerte est active sans l'autorisation,
 * on le dit ici et un bouton la demande (ou ouvre les réglages d'Android si elle a déjà été refusée).
 */
@Composable
internal fun NotificationPermissionBanner(needed: Boolean) {
    if (!needed || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val prefs = LocalUiPreferences.current
    var granted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok && prefs?.notificationPermissionAsked == true) {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
        prefs?.notificationPermissionAsked = true
    }
    if (granted) return
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.notif_banner_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.notif_banner_message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GlassButton(
                text = stringResource(R.string.notif_nudge_enable),
                onClick = { launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Version installée ; sept touchers dessus débloquent le journal de debug (outil de développeur, caché par défaut). */
@Composable
internal fun AboutRow(versionText: String, hint: String?, onSevenTaps: () -> Unit) {
    var taps by remember { mutableStateOf(0) }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            taps += 1
            if (taps >= 7) {
                taps = 0
                onSevenTaps()
            }
        },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(versionText, style = MaterialTheme.typography.bodyLarge)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
