package com.davidgcd.backlog.notifications

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "notification_prefs")

/**
 * Notification preferences, the Android equivalent of the iOS app's
 * NotificationPolicyStore.Preferences: the release reminder's timing (free,
 * like iOS) plus the two drift alerts (date change / new platform).
 */
class NotificationPreferences(private val context: Context) {
    private val releaseRemindersKey = booleanPreferencesKey("release_reminders_enabled")
    private val scheduleKey = stringPreferencesKey("release_reminder_schedule")
    private val hourKey = intPreferencesKey("release_reminder_hour")
    private val minuteKey = intPreferencesKey("release_reminder_minute")
    private val dateChangeAlertsKey = booleanPreferencesKey("date_change_alerts_enabled")
    private val platformChangeAlertsKey = booleanPreferencesKey("platform_change_alerts_enabled")
    private val dealAlertsKey = booleanPreferencesKey("deal_alerts_enabled")
    private val episodeAlertsKey = booleanPreferencesKey("episode_alerts_enabled")

    val releaseRemindersEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[releaseRemindersKey] ?: true
    }

    val schedule: Flow<ReleaseReminderSchedule> = context.dataStore.data.map { prefs ->
        ReleaseReminderSchedule.fromStoredName(prefs[scheduleKey])
    }

    /** Default 10:00, same default as the iOS app's release-day reminder. */
    val hour: Flow<Int> = context.dataStore.data.map { prefs -> prefs[hourKey] ?: 10 }
    val minute: Flow<Int> = context.dataStore.data.map { prefs -> prefs[minuteKey] ?: 0 }

    val dateChangeAlertsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[dateChangeAlertsKey] ?: true
    }

    val platformChangeAlertsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[platformChangeAlertsKey] ?: true
    }

    /** « Promo Steam » : prévenir quand un jeu souhaité est en promotion. */
    val dealAlertsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[dealAlertsKey] ?: true }

    /** « Nouvel épisode » : prévenir quand une série de la liste diffuse un épisode. */
    val episodeAlertsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[episodeAlertsKey] ?: true }

    suspend fun setDealAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[dealAlertsKey] = enabled }
    }

    suspend fun setEpisodeAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[episodeAlertsKey] = enabled }
    }

    suspend fun setReleaseRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[releaseRemindersKey] = enabled }
    }

    suspend fun setSchedule(schedule: ReleaseReminderSchedule) {
        context.dataStore.edit { it[scheduleKey] = schedule.name }
    }

    suspend fun setTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[hourKey] = hour
            it[minuteKey] = minute
        }
    }

    suspend fun setDateChangeAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[dateChangeAlertsKey] = enabled }
    }

    suspend fun setPlatformChangeAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[platformChangeAlertsKey] = enabled }
    }
}
