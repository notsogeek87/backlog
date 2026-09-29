package com.davidgcd.backlog.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.csv.CsvExportService
import com.davidgcd.backlog.data.csv.CsvImportResult
import com.davidgcd.backlog.data.csv.CsvImportService
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.notifications.NotificationPreferences
import com.davidgcd.backlog.notifications.ReleaseReminderSchedule
import com.davidgcd.backlog.notifications.ReleaseReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: NotificationPreferences,
    private val appContext: Context,
    private val repository: BacklogRepository,
    private val csvExportService: CsvExportService,
    private val csvImportService: CsvImportService,
) : ViewModel() {
    val releaseRemindersEnabled: StateFlow<Boolean> = preferences.releaseRemindersEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val schedule: StateFlow<ReleaseReminderSchedule> = preferences.schedule
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReleaseReminderSchedule.DEFAULT)

    val hour: StateFlow<Int> = preferences.hour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 10)

    val minute: StateFlow<Int> = preferences.minute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val dateChangeAlertsEnabled: StateFlow<Boolean> = preferences.dateChangeAlertsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val platformChangeAlertsEnabled: StateFlow<Boolean> = preferences.platformChangeAlertsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _importResult = MutableStateFlow<CsvImportResult?>(null)
    val importResult: StateFlow<CsvImportResult?> = _importResult

    fun setReleaseRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setReleaseRemindersEnabled(enabled) }
    }

    fun setSchedule(schedule: ReleaseReminderSchedule) {
        viewModelScope.launch { preferences.setSchedule(schedule) }
    }

    fun setTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            preferences.setTime(hour, minute)
            ReleaseReminderScheduler.reschedule(appContext, hour, minute)
        }
    }

    fun setDateChangeAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setDateChangeAlertsEnabled(enabled) }
    }

    fun setPlatformChangeAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setPlatformChangeAlertsEnabled(enabled) }
    }

    /** One-shot outcome of the last export (true = ok); the screen shows it once then calls [consumeExportResult]. */
    private val _exportSucceeded = MutableStateFlow<Boolean?>(null)
    val exportSucceeded: StateFlow<Boolean?> = _exportSucceeded

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            _exportSucceeded.value = try {
                val games = repository.allGames()
                csvExportService.export(uri, games)
                true
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (t: Throwable) {
                false
            }
        }
    }

    fun consumeExportResult() {
        _exportSucceeded.value = null
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importResult.value = null
            _importResult.value = csvImportService.import(uri)
            _isImporting.value = false
        }
    }

    fun dismissImportResult() {
        _importResult.value = null
    }
}

class SettingsViewModelFactory(
    private val preferences: NotificationPreferences,
    private val appContext: Context,
    private val repository: BacklogRepository,
    private val csvExportService: CsvExportService,
    private val csvImportService: CsvImportService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == SettingsViewModel::class.java)
        return SettingsViewModel(preferences, appContext, repository, csvExportService, csvImportService) as T
    }
}
