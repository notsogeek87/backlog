package com.davidgcd.backlog.util

import android.net.Uri
import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.GameJsonCache
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.repository.toBook
import com.davidgcd.backlog.model.Book
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.model.MediaTitle
import java.net.URLEncoder

/**
 * What the https link carries about the title so that someone without the app sees its details on the
 * web page (see server/lib/openPage.js): title, year, image, genres, synopsis, rating and a catalogue link.
 * Everything is optional and shortened to keep the link easy to send.
 */
data class DetailPreview(
    val title: String,
    val year: Int? = null,
    /** Film poster path (`/abc.jpg`), IGDB image id, or book cover URL, depending on the kind. */
    val image: String? = null,
    val genres: String? = null,
    val summary: String? = null,
    /** Out of 10. */
    val rating: Double? = null,
    /** One line: directors and duration, platforms, or authors. */
    val meta: String? = null,
    /** Catalogue page (igdb.com game, Open Library / Google Books book); films derive theirs from the id. */
    val url: String? = null,
) {
    /** The query string (with its leading `?`), empty parameters left out. */
    fun query(): String = listOf(
        "t" to title.take(100),
        "y" to year?.toString(),
        "c" to image,
        "g" to genres?.take(100),
        "s" to summary?.let(::shorten),
        "r" to rating?.takeIf { it > 0 }?.let { "%.1f".format(java.util.Locale.ROOT, it) },
        "m" to meta?.take(150),
        "u" to url,
    ).filter { !it.second.isNullOrBlank() }
        .joinToString("&", prefix = "?") { (k, v) -> "$k=" + URLEncoder.encode(v!!, "UTF-8").replace("+", "%20") }

    private fun shorten(text: String): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        return if (clean.length <= SUMMARY_MAX) clean else clean.take(SUMMARY_MAX - 1).trimEnd() + "…"
    }

    companion object {
        private const val SUMMARY_MAX = 400
        private val TMDB_POSTER = Regex("""^https://image\.tmdb\.org/t/p/(?:w\d+|original)(/.+)$""")

        fun of(game: GameEntity, url: String? = null) = DetailPreview(
            title = game.name,
            year = game.firstReleaseDate?.let(::yearOf),
            image = game.coverImageId,
            genres = GameJsonCache.genreNames(game).joinToString(", "),
            summary = game.summary,
            rating = game.totalRating?.div(10),
            meta = GameJsonCache.platformNames(game).joinToString(", "),
            url = url,
        )

        fun of(game: Game, url: String? = null) = DetailPreview(
            title = game.name,
            year = game.firstReleaseDate?.let(::yearOf),
            image = game.cover?.imageId,
            genres = game.genres?.joinToString(", ") { it.name },
            summary = game.summary,
            rating = game.totalRating?.div(10),
            meta = game.platforms?.joinToString(", ") { it.name },
            url = url ?: game.url,
        )

        fun of(movie: MovieEntity) = DetailPreview(
            title = movie.title,
            year = movie.year,
            image = posterPath(movie.posterUrl),
            genres = movie.genres?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(", "),
            summary = movie.plot,
            rating = movie.tmdbRating,
            meta = listOfNotNull(movie.directors, movie.runtimeMinutes?.let { "$it min" }).joinToString(" · "),
        )

        fun of(title: MediaTitle) = DetailPreview(
            title = title.title,
            year = title.year,
            image = posterPath(title.posterUrl),
            genres = title.genres.joinToString(", "),
            summary = title.plot,
            rating = title.rating,
            meta = listOfNotNull(title.directors, title.runtimeMinutes?.let { "$it min" }).joinToString(" · "),
        )

        fun of(book: BookEntity) = DetailPreview(
            title = book.title,
            year = book.publishedYear,
            image = book.coverUrl,
            genres = book.subjects?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(4)?.joinToString(", "),
            summary = book.description,
            meta = book.authors?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(", "),
            url = book.toBook().catalogUrl,
        )

        fun of(book: Book) = DetailPreview(
            title = book.title,
            year = book.publishedYear,
            image = book.coverUrl,
            genres = book.subjects.take(4).joinToString(", "),
            summary = book.description,
            meta = book.authorLine,
            url = book.catalogUrl,
        )

        /** `https://image.tmdb.org/t/p/w500/abc.jpg` → `/abc.jpg`. */
        fun posterPath(url: String?): String? = url?.let { TMDB_POSTER.find(it)?.groupValues?.get(1) ?: it.takeIf { p -> p.startsWith("/") } }

        private fun yearOf(epochSeconds: Long): Int =
            java.time.Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneOffset.UTC).year
    }
}

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
        fun url(link: DetailLink, preview: DetailPreview? = null): String {
            val (kind, id) = parts(link)
            return "https://$HOST/$PATH/$kind/${Uri.encode(id)}${preview?.query().orEmpty()}"
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
