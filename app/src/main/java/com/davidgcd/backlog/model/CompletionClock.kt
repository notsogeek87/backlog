package com.davidgcd.backlog.model

/**
 * Date de fin d'un jeu, d'un film ou d'un livre, pour le bilan annuel. Passer à « terminé » la fixe (la première
 * fois seulement : re-sélectionner le statut ne la déplace pas) ; en sortir l'efface.
 */
object CompletionClock {
    fun next(current: Long?, finished: Boolean, now: Long = System.currentTimeMillis()): Long? =
        if (finished) current ?: now else null
}
