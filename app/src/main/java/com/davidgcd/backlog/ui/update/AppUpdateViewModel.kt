package com.davidgcd.backlog.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lielu.githubupdater.UpdateInfo
import com.lielu.githubupdater.UpdateManager
import com.lielu.githubupdater.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Vérifie automatiquement les mises à jour à l'ouverture de l'app et pilote la fenêtre qui les propose.
 * L'état brut est celui de [UpdateManager], partagé avec l'écran Réglages.
 */
class AppUpdateViewModel(
    private val updateManager: UpdateManager,
    val updatesEnabled: Boolean,
) : ViewModel() {
    val state = updateManager.state

    private val _dismissed = MutableStateFlow(false)

    /** `true` une fois « Plus tard » touché : la fenêtre ne revient qu'au prochain lancement. */
    val dismissed: StateFlow<Boolean> = _dismissed

    private val _userStarted = MutableStateFlow(false)

    /** `true` dès que l'utilisateur a touché « Installer » ou « Rechercher » : seulement alors on lui montre une erreur. */
    val userStarted: StateFlow<Boolean> = _userStarted

    /** `false` tant qu'Android n'a pas autorisé Backlog à installer des applications (étape à expliquer). */
    fun canInstallPackages(): Boolean = updateManager.canInstallPackages()

    /**
     * Appelée à chaque passage de l'app au premier plan. `force = true` : sans cela la bibliothèque réutilise
     * sa réponse précédente (« à jour ») jusqu'à `checkIntervalHours`, et une release publiée entre-temps
     * n'est pas vue. Une requête GitHub par ouverture reste très en dessous du quota (60/h).
     */
    fun checkOnOpen() {
        if (!updatesEnabled) return
        // Ne pas écraser une fenêtre déjà affichée, un téléchargement ou une installation en cours.
        if (!isIdle()) return
        _userStarted.value = false
        // Les erreurs (hors ligne, quota GitHub…) sont publiées dans `state` ; ici on reste silencieux.
        viewModelScope.launch { runCatching { updateManager.checkForUpdate(force = true) } }
    }

    /** Bouton « Rechercher une mise à jour » des Réglages : même vérification forcée, mais l'utilisateur voit le résultat. */
    fun checkManually() {
        if (!updatesEnabled) return
        val s = state.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading || s is UpdateState.Installing) return
        _dismissed.value = false
        _userStarted.value = true
        viewModelScope.launch { runCatching { updateManager.checkForUpdate(force = true) } }
    }

    fun onDismiss() {
        _dismissed.value = true
    }

    fun onInstall(update: UpdateInfo) {
        _userStarted.value = true
        viewModelScope.launch {
            runCatching {
                val apk = updateManager.downloadUpdate(update)
                install(apk)
            }
        }
    }

    /** Relance l'installation d'un APK déjà téléchargé, typiquement au retour des réglages Android. */
    fun onInstallDownloaded(apk: File) {
        _userStarted.value = true
        runCatching { install(apk) }
    }

    private fun isIdle(): Boolean {
        val s = state.value
        return s is UpdateState.Idle || s is UpdateState.UpToDate || s is UpdateState.Error
    }

    private fun install(apk: File) {
        if (updateManager.canInstallPackages()) {
            updateManager.installUpdate(apk)
        } else {
            updateManager.openInstallPermissionSettings()
        }
    }
}

class AppUpdateViewModelFactory(
    private val updateManager: UpdateManager,
    private val updatesEnabled: Boolean,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AppUpdateViewModel(updateManager, updatesEnabled) as T
}
