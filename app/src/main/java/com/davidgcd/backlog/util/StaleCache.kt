package com.davidgcd.backlog.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Cache mémoire « périmé mais utilisable » : une liste déjà chargée est affichée tout de suite à la réouverture
 * d'un écran (Découvrir), et n'est rafraîchie en arrière-plan que si elle a dépassé [ttlMillis]. Un échec réseau
 * garde alors la liste affichée au lieu d'un message d'erreur.
 */
class StaleCache<K : Any, V : Any>(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** [fresh] : encore dans sa durée de validité (rien à recharger). */
    data class Entry<V>(val value: V, val fresh: Boolean)

    private val entries = ConcurrentHashMap<K, Pair<Long, V>>()

    fun get(key: K): Entry<V>? = entries[key]?.let { (storedAt, value) -> Entry(value, fresh = clock() - storedAt < ttlMillis) }

    fun put(key: K, value: V) {
        entries[key] = clock() to value
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 10 * 60_000L
    }
}
