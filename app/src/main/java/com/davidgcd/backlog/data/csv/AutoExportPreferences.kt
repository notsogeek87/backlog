package com.davidgcd.backlog.data.csv

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.autoExportDataStore by preferencesDataStore(name = "auto_export_prefs")

/** Outcome of the last automatic export run; [at] is epoch millis. */
data class AutoExportStatus(val at: Long, val succeeded: Boolean)

/**
 * Automatic-export settings: the folder the user picked (a persisted SAF tree URI), the
 * frequency, and the outcome of the last run so Settings can surface a broken folder.
 */
class AutoExportPreferences(private val context: Context) {
    private val enabledKey = booleanPreferencesKey("auto_export_enabled")
    private val folderKey = stringPreferencesKey("auto_export_folder_uri")
    private val frequencyKey = stringPreferencesKey("auto_export_frequency")
    private val lastAtKey = longPreferencesKey("auto_export_last_at")
    private val lastOkKey = booleanPreferencesKey("auto_export_last_ok")

    val enabled: Flow<Boolean> = context.autoExportDataStore.data.map { it[enabledKey] ?: false }
    val folderUri: Flow<String?> = context.autoExportDataStore.data.map { it[folderKey] }
    val frequency: Flow<AutoExportFrequency> = context.autoExportDataStore.data.map {
        AutoExportFrequency.fromStoredName(it[frequencyKey])
    }
    val lastStatus: Flow<AutoExportStatus?> = context.autoExportDataStore.data.map { prefs ->
        prefs[lastAtKey]?.let { AutoExportStatus(it, prefs[lastOkKey] ?: false) }
    }

    /** Enabling always comes with a folder; disabling forgets the folder and the last result. */
    suspend fun enable(folderUri: String, frequency: AutoExportFrequency) {
        context.autoExportDataStore.edit {
            it[enabledKey] = true
            it[folderKey] = folderUri
            it[frequencyKey] = frequency.name
            it.remove(lastAtKey)
            it.remove(lastOkKey)
        }
    }

    suspend fun setFolder(folderUri: String) {
        context.autoExportDataStore.edit { it[folderKey] = folderUri }
    }

    suspend fun setFrequency(frequency: AutoExportFrequency) {
        context.autoExportDataStore.edit { it[frequencyKey] = frequency.name }
    }

    suspend fun disable() {
        context.autoExportDataStore.edit {
            it[enabledKey] = false
            it.remove(folderKey)
            it.remove(lastAtKey)
            it.remove(lastOkKey)
        }
    }

    suspend fun recordResult(succeeded: Boolean, at: Long = System.currentTimeMillis()) {
        context.autoExportDataStore.edit {
            it[lastAtKey] = at
            it[lastOkKey] = succeeded
        }
    }
}
