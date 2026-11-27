package com.davidgcd.backlog.notifications

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Historique local des alertes (sorties, promotions, épisodes, changements) : la cloche de l'accueil le lit, le
 * passage quotidien y écrit. Local à l'appareil, jamais envoyé nulle part, exclu de la sauvegarde (c'est un journal).
 */
class NotificationHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun entries(): List<NotificationEntry> =
        NotificationHistory.prune(NotificationHistory.decode(prefs.getString(KEY_ENTRIES, null)), System.currentTimeMillis())

    fun lastSeenMillis(): Long = prefs.getLong(KEY_LAST_SEEN, 0L)

    @Synchronized
    fun record(entry: NotificationEntry) {
        val current = NotificationHistory.decode(prefs.getString(KEY_ENTRIES, null))
        val updated = NotificationHistory.add(current, entry, System.currentTimeMillis())
        prefs.edit().putString(KEY_ENTRIES, NotificationHistory.encode(updated)).apply()
    }

    /** L'historique a été ouvert : la pastille de la cloche retombe à zéro. */
    fun markAllSeen() {
        prefs.edit().putLong(KEY_LAST_SEEN, System.currentTimeMillis()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_ENTRIES).putLong(KEY_LAST_SEEN, System.currentTimeMillis()).apply()
    }

    /** Émet à chaque changement (nouvelle alerte, lecture, effacement), y compris quand le worker écrit app ouverte. */
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        const val PREFS_NAME = "notification_history"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_LAST_SEEN = "last_seen"
    }
}
