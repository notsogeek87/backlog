package com.davidgcd.backlog.ui.welcome

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// Hors sauvegarde Android (absent de data_extraction_rules.xml) : une réinstallation repropose l'écran,
// justement le moment où il faut reconnecter ses services.
private val Context.welcomeDataStore by preferencesDataStore(name = "welcome")

/** Première installation : l'écran « Connecte tes services » s'ouvre une seule fois au premier lancement. */
class WelcomeStore(private val context: Context) {
    private val key = booleanPreferencesKey("platforms_shown")

    private fun isUpdatedInstall(): Boolean = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime > info.firstInstallTime
    } catch (e: Exception) {
        false
    }

    /** Vrai une seule fois, au premier lancement d'une première installation ; marque l'écran comme vu. */
    suspend fun takeFirstLaunch(): Boolean {
        val shown = context.welcomeDataStore.data.first()[key] == true
        if (!shown) context.welcomeDataStore.edit { it[key] = true }
        return shouldShowWelcome(shown, isUpdatedInstall())
    }
}

/** Ni déjà vu, ni une mise à jour : quelqu'un qui utilisait déjà l'app n'est pas renvoyé vers cet écran. */
internal fun shouldShowWelcome(alreadyShown: Boolean, updatedInstall: Boolean): Boolean = !alreadyShown && !updatedInstall
