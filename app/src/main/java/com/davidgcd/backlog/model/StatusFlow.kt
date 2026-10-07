package com.davidgcd.backlog.model

/*
 * « Statut suivant » d'un geste de balayage : l'étape naturelle après le statut courant, ou null quand il n'y en a
 * pas (le geste ne fait alors rien). Un jeu souhaité qu'on balaye devient simplement possédé (Backlog).
 */

fun GameStatus.next(): GameStatus? = when (this) {
    GameStatus.BACKLOG -> GameStatus.PLAYED
    GameStatus.PLAYED -> GameStatus.COMPLETED
    GameStatus.COMPLETED -> null
    GameStatus.WISHLIST -> GameStatus.BACKLOG
}

fun WatchStatus.next(): WatchStatus? = when (this) {
    WatchStatus.TO_WATCH -> WatchStatus.WATCHING
    WatchStatus.WATCHING -> WatchStatus.WATCHED
    WatchStatus.WATCHED -> null
}

fun ReadStatus.next(): ReadStatus? = when (this) {
    ReadStatus.TO_READ -> ReadStatus.READING
    ReadStatus.READING -> ReadStatus.READ
    ReadStatus.READ -> null
    ReadStatus.ABANDONED -> ReadStatus.TO_READ
}

/** Statuts « en cours » : ce que l'accueil propose de continuer. */
val GameStatus.isInProgress: Boolean get() = this == GameStatus.PLAYED
val WatchStatus.isInProgress: Boolean get() = this == WatchStatus.WATCHING
val ReadStatus.isInProgress: Boolean get() = this == ReadStatus.READING

/**
 * Statut proposé à l'ajout d'un jeu : un jeu pas encore sorti (onglet « À venir »…) est un jeu qu'on attend
 * (Souhaité), pas un jeu qu'on possède. Un long appui sur « + » permet de choisir autre chose.
 */
fun GameStatus.Companion.suggestedFor(firstReleaseEpochSeconds: Long?, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): GameStatus =
    if (firstReleaseEpochSeconds != null && firstReleaseEpochSeconds > nowEpochSeconds) GameStatus.WISHLIST else GameStatus.BACKLOG
