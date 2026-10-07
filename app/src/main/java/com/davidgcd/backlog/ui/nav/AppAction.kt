package com.davidgcd.backlog.ui.nav

import com.davidgcd.backlog.ui.components.MediaType

/** Ce qu'un raccourci d'icône, un widget ou un texte partagé demande à l'app de faire à l'ouverture. */
sealed interface AppAction {
    /** Ouvre la recherche du catalogue de [media], éventuellement pré-remplie. */
    data class Search(val media: MediaType, val query: String?) : AppAction

    /** Ouvre « Mon année ». */
    data object Recap : AppAction

    /** Ouvre l'accueil sur « Ce soir, je fais quoi ? ». */
    data object Tonight : AppAction
}
