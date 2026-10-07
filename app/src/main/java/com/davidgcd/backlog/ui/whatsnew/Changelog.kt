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
        ChangelogEntry(5, "Bande-annonce", "Les fiches films et séries ont leur bande-annonce, à regarder directement dans la page, en français quand elle existe."),
        ChangelogEntry(6, "Bande-annonce des jeux", "Les fiches jeux ont aussi leur bande-annonce, à regarder directement dans la page, en français quand une version française existe."),
        ChangelogEntry(7, "Nouvel accueil « Aujourd'hui »", "Une page d'accueil avec « Ce soir, je fais quoi ? », ce que tu as en cours, les sorties à venir et tes derniers ajouts. Jeux, films & séries et livres vivent maintenant dans l'onglet Bibliothèque."),
        ChangelogEntry(8, "Recherche dans ma liste", "La loupe cherche d'abord dans ce que tu possèdes déjà (instantané) ; bascule sur « Catalogue » pour ajouter quelque chose de nouveau."),
        ChangelogEntry(9, "Filtrer et trier d'un coup", "Une seule feuille pour le tri et les filtres, avec le nombre de résultats. Les tuiles de statut du haut filtrent aussi la liste d'un toucher."),
        ChangelogEntry(10, "Gestes sur les listes", "Balaie une ligne vers la droite pour passer au statut suivant, vers la gauche pour archiver. Appui long pour les actions rapides, toucher le badge pour changer de statut. Tout est annulable."),
        ChangelogEntry(11, "Classement par glisser-déposer", "Dans ton classement de jeux, tire une ligne, envoie-la tout en haut ou tout en bas, ou touche son numéro pour la placer à une position précise."),
        ChangelogEntry(12, "Mon année", "Un bilan de ton année (terminés, genres préférés, coups de cœur) à partager en image."),
        ChangelogEntry(13, "Thèmes et couleurs", "Nouveau thème clair, mode AMOLED et couleurs dynamiques (Android 12+) dans Réglages > Apparence. Le fond des fiches reprend la couleur de la jaquette."),
        ChangelogEntry(14, "Alertes promo et épisodes", "Sois prévenu quand un jeu de ta liste de souhaits est en promotion sur Steam ou qu'une série de ta liste diffuse un nouvel épisode."),
        ChangelogEntry(15, "Widget et raccourcis", "Un widget « en cours / prochaine sortie », des raccourcis sur l'icône de l'app et « Partager vers Backlog » depuis un lien Steam, TMDB ou un titre."),
    )

    /** Dernière entrée connue des versions d'avant ce journal : une mise à jour depuis celles-ci présente la suite. */
    const val LEGACY_BASELINE_ID = 4

    val latestId: Int get() = entries.maxOfOrNull { it.id } ?: 0

    /** Entrées plus récentes que [lastSeenId], de la plus récente à la plus ancienne. */
    fun since(lastSeenId: Int, all: List<ChangelogEntry> = entries): List<ChangelogEntry> =
        all.filter { it.id > lastSeenId }.sortedByDescending { it.id }
}
