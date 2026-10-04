package com.davidgcd.backlog.util

import android.net.Uri

/** A detail page a shared link points to. */
sealed interface DetailLink {
    data class Game(val igdbId: Long) : DetailLink
    data class Movie(val titleKey: String) : DetailLink
    data class Book(val bookKey: String) : DetailLink

    companion object {
        const val HOST = "library.lielu.eu"
        const val SCHEME = "backlog"
        private const val PATH = "open"

        /**
         * The https link sent to a contact: a chat app makes it tappable, and the page behind it
         * offers to open the app (or says where to get it) when the phone doesn't do it by itself.
         */
        fun url(link: DetailLink): String {
            val (kind, id) = parts(link)
            return "https://$HOST/$PATH/$kind/${Uri.encode(id)}"
        }

        /** Reads a link built by [url], or its `backlog://open/<kind>/<id>` twin; null for anything else. */
        fun parse(uri: Uri?): DetailLink? {
            uri ?: return null
            val segments = uri.pathSegments
            val (kind, id) = when {
                uri.scheme == SCHEME && uri.host == PATH && segments.size == 2 -> segments[0] to segments[1]
                uri.scheme == "https" && uri.host == HOST && segments.size == 3 && segments[0] == PATH -> segments[1] to segments[2]
                else -> return null
            }
            return parse(kind, id)
        }

        fun parse(kind: String, id: String): DetailLink? = when {
            id.isBlank() -> null
            kind == "game" -> id.toLongOrNull()?.let(::Game)
            kind == "movie" -> if (id.substringBefore(':') in setOf("movie", "tv") && id.substringAfter(':', "").toLongOrNull() != null) Movie(id) else null
            kind == "book" -> Book(id)
            else -> null
        }

        private fun parts(link: DetailLink) = when (link) {
            is Game -> "game" to link.igdbId.toString()
            is Movie -> "movie" to link.titleKey
            is Book -> "book" to link.bookKey
        }
    }
}
