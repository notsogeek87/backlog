package com.davidgcd.backlog.data.share

import com.davidgcd.backlog.data.local.titleKind
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.BookRepository
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.model.Ranking
import com.davidgcd.backlog.model.TitleKey
import com.davidgcd.backlog.model.TitleKind
import com.davidgcd.backlog.util.AppLogger
import com.davidgcd.backlog.util.TmdbImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Publishes the whole library — games, films & séries and books — as one public page with a tab per typology.
 * Shared by the games and the films screens, so both offer the same "my library" choice.
 */
class LibrarySharer(
    private val games: BacklogRepository,
    private val movies: MovieRepository,
    private val books: BookRepository,
    private val shareLinkService: ShareLinkService,
) {
    /** The link to the library page, or null when the server can't be reached (the caller then shares text). */
    suspend fun publish(title: String): String? = try {
        val allGames = games.allGames().filter { !it.isArchived }
        val rankOf = Ranking.order(allGames).filter { it.userRank != null }.mapIndexed { i, g -> g.igdbId to i + 1 }.toMap()
        val links = gameLinks(allGames.map { it.igdbId })
        val gameItems = allGames.map { ShareItem(it.name, it.status, it.coverImageId, links[it.igdbId], rankOf[it.igdbId]) }
        val movieItems = movies.allMovies().filter { !it.isArchived }.map {
            ShareMovieItem(
                name = it.title,
                status = it.status,
                series = it.titleKind == TitleKind.SERIES,
                year = it.year,
                posterUrl = TmdbImage.poster(it.posterUrl, width = 342),
                url = TitleKey.url(it.titleKey),
                rating = it.userRating,
            )
        }
        val bookItems = books.allBooks().map { it.toShareItem() }
        shareLinkService.publishLibrary(title, gameItems, movieItems, bookItems)
    } catch (t: CancellationException) {
        throw t
    } catch (t: Throwable) {
        AppLogger.network.error("Share link: publishing the library failed, sharing plain text instead", t)
        null
    }

    /** igdb.com pages: the cached ones, plus what IGDB answers within a few seconds for the rest. */
    private suspend fun gameLinks(ids: List<Long>): Map<Long, String> {
        val cached = shareLinkService.cachedUrls()
        val missing = ids.filter { it !in cached }
        if (missing.isEmpty()) return cached
        return try {
            val fetched = withTimeoutOrNull(10_000) { games.gameUrls(missing) } ?: return cached
            shareLinkService.rememberUrls(fetched)
            cached + fetched
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            cached
        }
    }
}
