package com.davidgcd.backlog.notifications

import android.content.Context

/**
 * Ce dont les alertes se souviennent pour ne jamais prévenir deux fois de la même chose : le dernier prix annoncé d'un
 * jeu en promotion, le dernier épisode annoncé d'une série. Local à l'appareil, jamais envoyé nulle part.
 */
class AlertMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("alert_memory", Context.MODE_PRIVATE)

    fun dealPrice(appId: Long): Long? = prefs.getLong("deal_$appId", -1L).takeIf { it >= 0 }

    fun rememberDeal(appId: Long, finalCents: Long) {
        prefs.edit().putLong("deal_$appId", finalCents).apply()
    }

    fun forgetDeal(appId: Long) {
        prefs.edit().remove("deal_$appId").apply()
    }

    fun episode(titleKey: String): String? = prefs.getString("episode_$titleKey", null)

    fun rememberEpisode(titleKey: String, label: String) {
        prefs.edit().putString("episode_$titleKey", label).apply()
    }
}
