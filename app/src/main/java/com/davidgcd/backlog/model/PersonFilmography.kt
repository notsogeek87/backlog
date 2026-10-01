package com.davidgcd.backlog.model

/** A person of the credits with the films & séries they are credited on — as an actor, or as a director. */
data class PersonFilmography(
    val name: String,
    /** TMDB `profile_path`, null when there is no photo. */
    val photoPath: String?,
    /** Newest first; titles without a date come last. */
    val titles: List<MediaTitle>,
)
