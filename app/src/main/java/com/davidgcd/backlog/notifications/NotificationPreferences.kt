package com.davidgcd.backlog.notifications

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "notification_prefs")

/**
 * Free release-day reminder toggle, the Android equivalent of the iOS app's
 * NotificationPolicyStore.Preferences (kept intentionally small here — no
 * lead-time choice yet, see README).
 */
class NotificationPreferences(private val context: Context) {
    private val releaseRemindersKey = booleanPreferencesKey("release_reminders_enabled")

    val releaseRemindersEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[releaseRemindersKey] ?: true
    }

    suspend fun setReleaseRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[releaseRemindersKey] = enabled }
    }
}
