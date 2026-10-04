package com.davidgcd.backlog.ui.whatsnew

/** Une nouveauté visible par l'utilisateur. [id] croît à chaque ajout (jamais réutilisé ni réordonné). */
data class ChangelogEntry(val id: Int, val title: String, val description: String)

/**
 * Journal des nouveautés embarqué dans l'app. À chaque fonctionnalité visible, ajouter une entrée en FIN de
 * liste avec le prochain [ChangelogEntry.id] : après une mise à jour, l'app présente les entrées dont l'id
 * dépasse le dernier vu, c'est-à-dire exactement ce qui est apparu entre l'ancienne version installée et la nouvelle.
 */
object Changelog {
    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(1, "Mises à jour automatiques", "L'app vérifie à chaque ouverture si une version plus récente existe et te propose de l'installer."),
        ChangelogEntry(2, "Recherche dans toutes les catégories", "La recherche affiche d'abord l'onglet courant, puis les résultats des autres catégories (jeux, films/séries, livres)."),
        ChangelogEntry(3, "Partage d'une fiche", "Partage un jeu, un film, une série ou un livre par lien : il s'ouvre directement dans l'app de ton contact."),
        ChangelogEntry(4, "Navigation adaptée aux grands écrans", "Rail latéral en paysage et sur les appareils pliables ouverts."),
        ChangelogEntry(5, "Bande-annonce", "Les fiches films et séries proposent la bande-annonce, en français quand elle existe."),
        ChangelogEntry(6, "Bande-annonce des jeux", "Les fiches jeux proposent aussi leur bande-annonce, en français quand une version française existe."),
    )

    /** Dernière entrée connue des versions d'avant ce journal : une mise à jour depuis celles-ci présente la suite. */
    const val LEGACY_BASELINE_ID = 4

    val latestId: Int get() = entries.maxOfOrNull { it.id } ?: 0

    /** Entrées plus récentes que [lastSeenId], de la plus récente à la plus ancienne. */
    fun since(lastSeenId: Int, all: List<ChangelogEntry> = entries): List<ChangelogEntry> =
        all.filter { it.id > lastSeenId }.sortedByDescending { it.id }
}
