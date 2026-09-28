package com.davidgcd.backlog.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.davidgcd.backlog.R
import com.davidgcd.backlog.notifications.ReleaseReminderSchedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val releaseRemindersEnabled by viewModel.releaseRemindersEnabled.collectAsState()
    val schedule by viewModel.schedule.collectAsState()
    val hour by viewModel.hour.collectAsState()
    val minute by viewModel.minute.collectAsState()
    val dateChangeAlertsEnabled by viewModel.dateChangeAlertsEnabled.collectAsState()
    val platformChangeAlertsEnabled by viewModel.platformChangeAlertsEnabled.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importResult by viewModel.importResult.collectAsState()

    var showTimePicker by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.exportCsv(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importCsv(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SectionTitle(stringResource(R.string.settings_section_games), topPadding = 16.dp)

            SettingsSwitchRow(
                title = stringResource(R.string.settings_release_reminders_title),
                subtitle = stringResource(R.string.settings_release_reminders_subtitle),
                checked = releaseRemindersEnabled,
                onCheckedChange = viewModel::setReleaseRemindersEnabled,
            )

            if (releaseRemindersEnabled) {
                ScheduleRow(current = schedule, onSelect = viewModel::setSchedule)
                TimeRow(hour = hour, minute = minute, onClick = { showTimePicker = true })
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle(stringResource(R.string.settings_section_changes))

            SettingsSwitchRow(
                title = stringResource(R.string.settings_date_change_title),
                subtitle = stringResource(R.string.settings_date_change_subtitle),
                checked = dateChangeAlertsEnabled,
                onCheckedChange = viewModel::setDateChangeAlertsEnabled,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_platform_change_title),
                subtitle = stringResource(R.string.settings_platform_change_subtitle),
                checked = platformChangeAlertsEnabled,
                onCheckedChange = viewModel::setPlatformChangeAlertsEnabled,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle(stringResource(R.string.settings_section_data))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextButton(onClick = { exportLauncher.launch("backlog.csv") }) {
                    Text(stringResource(R.string.settings_export_csv))
                }
                TextButton(
                    onClick = { importLauncher.launch(arrayOf("text/*", "text/comma-separated-values")) },
                    enabled = !isImporting,
                ) {
                    Text(stringResource(R.string.settings_import_csv))
                }
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp).size(20.dp))
                }
            }
        }
    }

    importResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImportResult,
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
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = timePickerState)
                Row(modifier = Modifier.padding(top = 16.dp)) {
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

@Composable
private fun SectionTitle(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = topPadding, bottom = 4.dp),
    )
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
