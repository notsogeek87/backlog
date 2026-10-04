package com.davidgcd.backlog.ui.whatsnew

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.whatsNewDataStore by preferencesDataStore(name = "whats_new")

/** Dernière entrée du journal déjà présentée à l'utilisateur. */
class WhatsNewStore(private val context: Context) {
    private fun isUpdatedInstall(): Boolean = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime > info.firstInstallTime
    } catch (e: Exception) {
        false
    }

    private val key = intPreferencesKey("last_seen_id")

    /**
     * Les nouveautés à présenter maintenant, puis marque tout comme vu. Une première installation n'a rien
     * à présenter (rien n'est « nouveau » pour qui découvre l'app) : on enregistre juste le point de départ.
     * Sans point de départ mais avec une app déjà mise à jour (version d'avant ce journal), on part de
     * [Changelog.LEGACY_BASELINE_ID].
     */
    suspend fun takeUnseen(): List<ChangelogEntry> {
        val stored = context.whatsNewDataStore.data.first()[key]
        val latest = Changelog.latestId
        if (stored == latest) return emptyList()
        context.whatsNewDataStore.edit { it[key] = latest }
        val from = stored ?: if (isUpdatedInstall()) Changelog.LEGACY_BASELINE_ID else return emptyList()
        return Changelog.since(from)
    }
}
