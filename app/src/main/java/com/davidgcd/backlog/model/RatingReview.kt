package com.davidgcd.backlog.model

import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.local.authorList
import com.davidgcd.backlog.data.local.gameStatus
import com.davidgcd.backlog.data.local.readStatus
import com.davidgcd.backlog.data.local.watchStatus
import com.davidgcd.backlog.util.TmdbImage
import kotlin.random.Random

/** Un jeu, un film / une série ou un livre à re-noter, quel que soit son média. */
data class ReviewItem(
    val medium: Medium,
    /** Id IGDB, titleKey TMDB ou bookKey. */
    val key: String,
    val title: String,
    val coverImageId: String? = null,
    val coverUrl: String? = null,
    val subtitle: String? = null,
    /** Note actuelle ; null = jamais notée. */
    val rating: Int? = null,
    /** Date (UTC, ms) de la dernière note ou confirmation ; null = jamais datée. */
    val ratedAt: Long? = null,
) {
    /** Échelle de la note : 10 pour les jeux et les films & séries, 5 étoiles pour les livres. */
    val maxRating: Int get() = if (medium == Medium.BOOK) BOOK_MAX_RATING else MAX_RATING

    companion object {
        const val MAX_RATING = 10
        const val BOOK_MAX_RATING = 5
    }
}

/**
 * « Réviser mes notes » : repasse ce qui a déjà été vu pour remettre les notes à jour (hors archives) :
 * les jeux de la bibliothèque (tout sauf la liste de souhaits), les films & séries vus et les livres lus.
 * On commence par les notes les plus anciennes ; ce qui n'a jamais été noté ni daté passe d'abord, dans un
 * ordre aléatoire (c'est aussi le départage de deux dates égales), puis on avance vers les plus récentes.
 */
object RatingReview {
    fun queue(
        games: List<GameEntity>,
        movies: List<MovieEntity>,
        books: List<BookEntity>,
        random: Random = Random.Default,
    ): List<ReviewItem> {
        val items = games.filter { !it.isArchived && it.gameStatus != GameStatus.WISHLIST }.map { it.toReviewItem() } +
            movies.filter { !it.isArchived && it.watchStatus == WatchStatus.WATCHED }.map { it.toReviewItem() } +
            books.filter { it.readStatus == ReadStatus.READ }.map { it.toReviewItem() }
        // Mélange d'abord, puis tri stable : l'aléatoire ne joue que parmi les dates identiques (ou absentes).
        return items.shuffled(random).sortedBy { it.ratedAt ?: Long.MIN_VALUE }
    }

    private fun GameEntity.toReviewItem() = ReviewItem(
        Medium.GAME, igdbId.toString(), name, coverImageId = coverImageId, rating = userRating, ratedAt = ratedAt,
    )

    private fun MovieEntity.toReviewItem() = ReviewItem(
        Medium.MOVIE, titleKey, title,
        coverUrl = TmdbImage.poster(posterUrl, width = 342),
        subtitle = year?.toString(),
        rating = userRating,
        ratedAt = ratedAt,
    )

    private fun BookEntity.toReviewItem() = ReviewItem(
        Medium.BOOK, bookKey, title, coverUrl = coverUrl, subtitle = authorList.firstOrNull(), rating = userRating, ratedAt = ratedAt,
    )
}
