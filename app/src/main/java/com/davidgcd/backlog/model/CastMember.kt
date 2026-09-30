package com.davidgcd.backlog.model

/** A person of the credits: the director (the creator for a series) or one of the top-billed actors. */
data class CastMember(
    val name: String,
    /** The character played; null for the director. */
    val role: String?,
    /** TMDB `profile_path` (`/abc.jpg`), null when there is no photo. */
    val photoPath: String?,
    val isDirector: Boolean,
    /** TMDB person id; null if the answer carried none. */
    val personId: Long? = null,
) {
    val tmdbUrl: String? get() = personId?.let { "https://www.themoviedb.org/person/$it" }
}
