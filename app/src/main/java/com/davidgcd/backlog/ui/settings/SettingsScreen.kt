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
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.notifications.ReleaseReminderSchedule
import com.davidgcd.backlog.util.DebugLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)? = null,
    /** "My platforms" block, supplied by the nav host so Settings doesn't know about providers. */
    platformsContent: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val exportSucceeded by viewModel.exportSucceeded.collectAsState()
    val exportDoneMsg = stringResource(R.string.settings_export_done)
    val exportFailedMsg = stringResource(R.string.settings_export_failed)
    LaunchedEffect(exportSucceeded) {
        exportSucceeded?.let {
            snackbarHostState.showSnackbar(if (it) exportDoneMsg else exportFailedMsg)
            viewModel.consumeExportResult()
        }
    }
    val releaseRemindersEnabled by viewModel.releaseRemindersEnabled.collectAsState()
    val schedule by viewModel.schedule.collectAsState()
    val hour by viewModel.hour.collectAsState()
    val minute by viewModel.minute.collectAsState()
    val dateChangeAlertsEnabled by viewModel.dateChangeAlertsEnabled.collectAsState()
    val platformChangeAlertsEnabled by viewModel.platformChangeAlertsEnabled.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importResult by viewModel.importResult.collectAsState()

    var showTimePicker by remember { mutableStateOf(false) }
    var showDebugLog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.exportCsv(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importCsv(it) } }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Glass.Text,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            platformsContent()

            SectionTitle(stringResource(R.string.settings_section_games), topPadding = 8.dp)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_release_reminders_title),
                        subtitle = stringResource(R.string.settings_release_reminders_subtitle),
                        checked = releaseRemindersEnabled,
                        onCheckedChange = viewModel::setReleaseRemindersEnabled,
                    )
                    if (releaseRemindersEnabled) {
                        HorizontalDivider(color = Glass.Border)
                        ScheduleRow(current = schedule, onSelect = viewModel::setSchedule)
                        HorizontalDivider(color = Glass.Border)
                        TimeRow(hour = hour, minute = minute, onClick = { showTimePicker = true })
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_section_changes), topPadding = 8.dp)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_date_change_title),
                        subtitle = stringResource(R.string.settings_date_change_subtitle),
                        checked = dateChangeAlertsEnabled,
                        onCheckedChange = viewModel::setDateChangeAlertsEnabled,
                    )
                    HorizontalDivider(color = Glass.Border)
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_platform_change_title),
                        subtitle = stringResource(R.string.settings_platform_change_subtitle),
                        checked = platformChangeAlertsEnabled,
                        onCheckedChange = viewModel::setPlatformChangeAlertsEnabled,
                    )
                }
            }

            SectionTitle(stringResource(R.string.settings_section_data), topPadding = 8.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassButton(
                    text = stringResource(R.string.settings_export_csv),
                    onClick = { exportLauncher.launch("backlog.csv") },
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = stringResource(R.string.settings_import_csv),
                    onClick = {
                        if (!isImporting) importLauncher.launch(arrayOf("text/*", "text/comma-separated-values"))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Glass.Cyan,
                    trackColor = Glass.GlassTop,
                )
            }

            SectionTitle(stringResource(R.string.settings_section_debug), topPadding = 8.dp)
            GlassButton(
                text = stringResource(R.string.settings_view_debug_log),
                onClick = { showDebugLog = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDebugLog) {
        DebugLogDialog(onDismiss = { showDebugLog = false })
    }

    importResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImportResult,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            confirmButton = {
                TextButton(onClick = viewModel::dismissImportResult) { Text(stringResource(R.string.action_ok)) }
            },
            title = { Text(stringResource(R.string.settings_import_done_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_import_done_summary, result.added, result.skipped))
                    if (result.failedNames.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.settings_import_done_failed_prefix,
                                result.failedNames.joinToString(", "),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
        )
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        Dialog(onDismissRequest = { showTimePicker = false }) {
            // Dialog has no background of its own: without a Surface the picker floats transparent over the list.
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) }
                        TextButton(onClick = {
                            viewModel.setTime(timePickerState.hour, timePickerState.minute)
                            showTimePicker = false
                        }) { Text(stringResource(R.string.action_save)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = topPadding),
    )
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
private fun ScheduleRow(current: ReleaseReminderSchedule, onSelect: (ReleaseReminderSchedule) -> Unit) {
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
private fun ReleaseReminderSchedule.label(): String = stringResource(
    when (this) {
        ReleaseReminderSchedule.RELEASE_DAY -> R.string.schedule_release_day
        ReleaseReminderSchedule.DAY_BEFORE -> R.string.schedule_day_before
        ReleaseReminderSchedule.WEEK_BEFORE -> R.string.schedule_week_before
    },
)

@Composable
private fun TimeRow(hour: Int, minute: Int, onClick: () -> Unit) {
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
private fun DebugLogDialog(onDismiss: () -> Unit) {
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

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Backlog debug log", text))
}
