package com.davidgcd.backlog.util

/**
 * IGDB only serves genre/platform names in English, but the app is entirely in French — so
 * the wording is translated for display only. The stored/filtered values stay IGDB's raw names
 * (they are the identity used by filters, CSV export and drift detection); anything unknown
 * falls back to the raw name rather than disappearing.
 */
object FrenchLabels {
    private val genres = mapOf(
        "Point-and-click" to "Point and click",
        "Fighting" to "Combat",
        "Shooter" to "Tir",
        "Music" to "Musique",
        "Platform" to "Plateforme",
        "Puzzle" to "Réflexion",
        "Racing" to "Course",
        "Real Time Strategy (RTS)" to "Stratégie temps réel (RTS)",
        "Role-playing (RPG)" to "Jeu de rôle (RPG)",
        "Simulator" to "Simulation",
        "Sport" to "Sport",
        "Strategy" to "Stratégie",
        "Turn-based strategy (TBS)" to "Stratégie au tour par tour (TBS)",
        "Tactical" to "Tactique",
        "Hack and slash/Beat 'em up" to "Hack and slash / Beat 'em up",
        "Quiz/Trivia" to "Quiz",
        "Pinball" to "Flipper",
        "Adventure" to "Aventure",
        "Indie" to "Indépendant",
        "Arcade" to "Arcade",
        "Visual Novel" to "Roman visuel",
        "Card & Board Game" to "Jeu de cartes et de société",
        "MOBA" to "MOBA",
    )

    private val platforms = mapOf(
        "PC (Microsoft Windows)" to "PC (Windows)",
        "Web browser" to "Navigateur web",
        "Windows Mobile" to "Windows Mobile",
    )

    /** IMDb's genre names (films & séries) — a different vocabulary from IGDB's, same display-only rule. */
    private val movieGenres = mapOf(
        "Action" to "Action",
        "Adventure" to "Aventure",
        "Animation" to "Animation",
        "Biography" to "Biographie",
        "Comedy" to "Comédie",
        "Crime" to "Policier",
        "Documentary" to "Documentaire",
        "Drama" to "Drame",
        "Family" to "Famille",
        "Fantasy" to "Fantastique",
        "Film-Noir" to "Film noir",
        "Game-Show" to "Jeu télévisé",
        "History" to "Historique",
        "Horror" to "Horreur",
        "Music" to "Musique",
        "Musical" to "Comédie musicale",
        "Mystery" to "Mystère",
        "News" to "Actualités",
        "Reality-TV" to "Téléréalité",
        "Romance" to "Romance",
        "Sci-Fi" to "Science-fiction",
        "Short" to "Court métrage",
        "Sport" to "Sport",
        "Talk-Show" to "Talk-show",
        "Thriller" to "Thriller",
        "War" to "Guerre",
        "Western" to "Western",
    )

    fun genre(name: String): String = genres[name] ?: name

    fun movieGenre(name: String): String = movieGenres[name] ?: name

    fun platform(name: String): String = platforms[name] ?: name
}
