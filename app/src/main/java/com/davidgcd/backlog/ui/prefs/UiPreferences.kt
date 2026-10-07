package com.davidgcd.backlog.ui.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Apparence choisie dans les Réglages. Persistée par [name], jamais par son libellé. */
enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,

    /** Noir pur (écrans OLED) : même verre que le sombre, sur fond #000. */
    AMOLED,
    ;

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: DARK
    }
}

/**
 * Petites préférences d'interface lues de façon synchrone au premier affichage (thème, aperçu des
 * bandes-annonces, drapeaux « déjà demandé »). SharedPreferences plutôt que DataStore : le thème doit être
 * connu avant la première frame, sans clignotement.
 */
class UiPreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(ThemeMode.fromName(prefs.getString(KEY_THEME, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _dynamicColors = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC, false))

    /** Android 12+ : l'accent de l'app reprend la couleur du fond d'écran. */
    val dynamicColors: StateFlow<Boolean> = _dynamicColors

    private val _trailerPreview = MutableStateFlow(prefs.getBoolean(KEY_TRAILER_PREVIEW, false))

    /** Aperçu muet de la bande-annonce dès l'ouverture d'une fiche (consomme des données : désactivé par défaut). */
    val trailerPreview: StateFlow<Boolean> = _trailerPreview

    private val _debugUnlocked = MutableStateFlow(prefs.getBoolean(KEY_DEBUG, false))

    /** Journal de debug : masqué tant que l'utilisateur n'a pas tapoté 7 fois sur la version. */
    val debugUnlocked: StateFlow<Boolean> = _debugUnlocked

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setDynamicColors(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
        _dynamicColors.value = enabled
    }

    fun setTrailerPreview(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRAILER_PREVIEW, enabled).apply()
        _trailerPreview.value = enabled
    }

    fun unlockDebug() {
        prefs.edit().putBoolean(KEY_DEBUG, true).apply()
        _debugUnlocked.value = true
    }

    /** La permission de notifications n'est demandée qu'une fois, quand l'utilisateur active un rappel. */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF_ASKED, value).apply()

    private companion object {
        const val FILE = "ui_prefs"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_colors"
        const val KEY_TRAILER_PREVIEW = "trailer_preview"
        const val KEY_DEBUG = "debug_unlocked"
        const val KEY_NOTIF_ASKED = "notification_permission_asked"
    }
}

/** Les préférences d'interface, accessibles à tout l'arbre Compose (fournies par `MainActivity`). */
val LocalUiPreferences = androidx.compose.runtime.staticCompositionLocalOf<UiPreferences?> { null }
