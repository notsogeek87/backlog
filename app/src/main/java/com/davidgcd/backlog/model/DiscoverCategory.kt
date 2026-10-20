package com.davidgcd.backlog.model

/** The lists the Discover tab can show. Stable names; the French labels live in strings.xml. */
enum class DiscoverCategory {
    PERSONALIZED,  // Recommandations basées sur les goûts de l'utilisateur
    POPULAR,
    TOP_RATED,
    TRENDING,
    NEW_RELEASES,
    UPCOMING,
}
