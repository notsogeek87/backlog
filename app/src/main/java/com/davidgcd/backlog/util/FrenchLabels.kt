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

    fun genre(name: String): String = genres[name] ?: name

    fun platform(name: String): String = platforms[name] ?: name
}
