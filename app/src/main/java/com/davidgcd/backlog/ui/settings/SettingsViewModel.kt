package com.davidgcd.backlog.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.notifications.NotificationPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val preferences: NotificationPreferences) : ViewModel() {
    val releaseRemindersEnabled: StateFlow<Boolean> = preferences.releaseRemindersEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setReleaseRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setReleaseRemindersEnabled(enabled) }
    }
}

class SettingsViewModelFactory(private val preferences: NotificationPreferences) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == SettingsViewModel::class.java)
        return SettingsViewModel(preferences) as T
    }
}
