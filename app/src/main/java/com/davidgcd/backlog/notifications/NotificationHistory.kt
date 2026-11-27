package com.davidgcd.backlog.notifications

import com.davidgcd.backlog.model.Medium
import org.json.JSONArray
import org.json.JSONObject

/** Ce qu'une alerte annonçait : sert à l'icône et au libellé dans l'historique. */
enum class NotificationKind { RELEASE, DATE_CHANGE, NEW_PLATFORM, DEAL, EPISODE }

/**
 * Une alerte envoyée par l'app, gardée dans l'historique de la cloche même après que la notification système a été
 * effacée. [itemKey] est l'igdbId (jeu) ou le titleKey (film / série) de la fiche à ouvrir.
 */
data class NotificationEntry(
    val id: String,
    val kind: NotificationKind,
    val medium: Medium,
    val itemKey: String,
    val title: String,
    val text: String,
    val postedAtMillis: Long,
)

/** Règles de l'historique (pures, testées sur la JVM) : ajout sans doublon, plafond, purge, (dé)sérialisation JSON. */
object NotificationHistory {
    /** Au-delà, les plus anciennes alertes sortent de l'historique. */
    const val MAX_ENTRIES = 200

    /** Une alerte de plus de 90 jours n'apporte plus rien. */
    const val MAX_AGE_MILLIS = 90L * 24 * 60 * 60 * 1000

    /**
     * Identifiant stable d'une alerte : une même alerte (même genre, même fiche, même [detail]) n'entre qu'une fois,
     * même si le passage quotidien tourne deux fois.
     */
    fun entryId(kind: NotificationKind, itemKey: String, detail: String): String = "${kind.name}|$itemKey|$detail"

    /** Ajoute [entry] en tête (la plus récente d'abord), ignore un doublon, purge ce qui est trop vieux ou en trop. */
    fun add(entries: List<NotificationEntry>, entry: NotificationEntry, nowMillis: Long): List<NotificationEntry> {
        if (entries.any { it.id == entry.id }) return prune(entries, nowMillis)
        return prune(listOf(entry) + entries, nowMillis)
    }

    fun prune(entries: List<NotificationEntry>, nowMillis: Long): List<NotificationEntry> =
        entries
            .filter { nowMillis - it.postedAtMillis <= MAX_AGE_MILLIS }
            .sortedByDescending { it.postedAtMillis }
            .take(MAX_ENTRIES)

    /** Nombre d'alertes arrivées depuis la dernière ouverture de l'historique. */
    fun unreadCount(entries: List<NotificationEntry>, lastSeenMillis: Long): Int =
        entries.count { it.postedAtMillis > lastSeenMillis }

    fun encode(entries: List<NotificationEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("kind", entry.kind.name)
                    .put("medium", entry.medium.name)
                    .put("key", entry.itemKey)
                    .put("title", entry.title)
                    .put("text", entry.text)
                    .put("at", entry.postedAtMillis),
            )
        }
        return array.toString()
    }

    /** Lit l'historique ; une entrée illisible (version future, donnée abîmée) est ignorée, jamais fatale. */
    fun decode(json: String?): List<NotificationEntry> {
        if (json.isNullOrBlank()) return emptyList()
        val array = try {
            JSONArray(json)
        } catch (e: Exception) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            try {
                NotificationEntry(
                    id = obj.getString("id"),
                    kind = NotificationKind.valueOf(obj.getString("kind")),
                    medium = Medium.valueOf(obj.getString("medium")),
                    itemKey = obj.getString("key"),
                    title = obj.getString("title"),
                    text = obj.getString("text"),
                    postedAtMillis = obj.getLong("at"),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
