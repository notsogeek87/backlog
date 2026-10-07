package com.davidgcd.backlog.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Le Snackbar de l'application entière (celui de la navigation), qui survit à l'écran qui l'a déclenché : retirer une fiche
 * ramène à la liste, et le message « retiré — Annuler » s'affiche là, avec son annulation qui tourne dans la portée de la navigation.
 */
class AppSnackbar(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String, actionLabel: String? = null, onAction: suspend () -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(message = message, actionLabel = actionLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

val LocalAppSnackbar = staticCompositionLocalOf<AppSnackbar?> { null }
