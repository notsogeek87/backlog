package com.davidgcd.backlog.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.local.authorList
import com.davidgcd.backlog.data.local.gameStatus
import com.davidgcd.backlog.data.local.readStatus
import com.davidgcd.backlog.data.local.watchStatus
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.BookRepository
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.model.Medium
import com.davidgcd.backlog.model.ReadStatus
import com.davidgcd.backlog.model.Tonight
import com.davidgcd.backlog.model.TonightCandidate
import com.davidgcd.backlog.model.WatchStatus
import com.davidgcd.backlog.model.isInProgress
import com.davidgcd.backlog.util.TmdbImage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Un élément de la bibliothèque tel que l'accueil l'affiche, quel que soit son média. */
data class HomeItem(
    val medium: Medium,
    /** Clé de navigation : id IGDB, titleKey TMDB ou bookKey. */
    val key: String,
    val title: String,
    val coverImageId: String? = null,
    val coverUrl: String? = null,
    val subtitle: String? = null,
    val addedAt: Long = 0L,
)

/** Une sortie à venir : [epochSeconds] (UTC) sert à afficher la date et à calculer « dans N jours ». */
data class UpcomingItem(val item: HomeItem, val epochSeconds: Long)

data class HomeState(
    val isEmpty: Boolean = true,
    val inProgress: List<HomeItem> = emptyList(),
    val upcoming: List<UpcomingItem> = emptyList(),
    val recent: List<HomeItem> = emptyList(),
    val tonightPool: List<TonightCandidate> = emptyList(),
)

/** Ce que l'accueil agrège : le meilleur de la bibliothèque (jeux, films & séries, livres) du moment. */
class HomeViewModel(
    games: BacklogRepository,
    movies: MovieRepository,
    books: BookRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val state: StateFlow<HomeState> = combine(games.observeBacklog(), movies.observeAll(), books.observeAll()) { g, m, b ->
        build(g, m, b, clock())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    companion object {
        private const val UPCOMING_DAYS = 30L
        private const val DAY_SECONDS = 86_400L
        private const val MAX_ROWS = 12

        internal fun build(games: List<GameEntity>, movies: List<MovieEntity>, books: List<BookEntity>, nowMillis: Long): HomeState {
            val activeGames = games.filter { !it.isArchived }
            val activeMovies = movies.filter { !it.isArchived }
            val activeBooks = books

            val inProgress = (
                activeGames.filter { it.gameStatus.isInProgress }.map { it.toItem() } +
                    activeMovies.filter { it.watchStatus.isInProgress }.map { it.toItem() } +
                    activeBooks.filter { it.readStatus.isInProgress }.map { it.toItem() }
                ).sortedByDescending { it.addedAt }.take(MAX_ROWS)

            val nowSeconds = nowMillis / 1000
            val horizon = nowSeconds + UPCOMING_DAYS * DAY_SECONDS
            // Sorties de la journée comprises : on remonte au début du jour UTC (les dates IGDB / TMDB sont des jours UTC).
            val dayStart = nowSeconds - nowSeconds % DAY_SECONDS
            val upcoming = (
                activeGames.filter { it.gameStatus != GameStatus.COMPLETED }.mapNotNull { g ->
                    g.firstReleaseDate?.takeIf { it in dayStart..horizon }?.let { UpcomingItem(g.toItem(), it) }
                } + activeMovies.filter { it.watchStatus != WatchStatus.WATCHED }.mapNotNull { m ->
                    m.releaseDate?.takeIf { it in dayStart..horizon }?.let { UpcomingItem(m.toItem(), it) }
                }
                ).sortedBy { it.epochSeconds }.take(MAX_ROWS)

            val recent = (activeGames.map { it.toItem() } + activeMovies.map { it.toItem() } + activeBooks.map { it.toItem() })
                .sortedByDescending { it.addedAt }.take(MAX_ROWS)

            val pool = activeGames.filter { it.gameStatus == GameStatus.BACKLOG || it.gameStatus.isInProgress }.map { it.toCandidate() } +
                activeMovies.filter { it.watchStatus != WatchStatus.WATCHED }.map { it.toCandidate() } +
                activeBooks.filter { it.readStatus == ReadStatus.TO_READ || it.readStatus.isInProgress }.map { it.toCandidate() }

            return HomeState(
                isEmpty = games.isEmpty() && movies.isEmpty() && books.isEmpty(),
                inProgress = inProgress,
                upcoming = upcoming,
                recent = recent,
                tonightPool = pool,
            )
        }

        private fun GameEntity.toItem() = HomeItem(Medium.GAME, igdbId.toString(), name, coverImageId = coverImageId, addedAt = addedAt)

        private fun MovieEntity.toItem() = HomeItem(
            Medium.MOVIE, titleKey, title,
            coverUrl = TmdbImage.poster(posterUrl, width = 342),
            subtitle = year?.toString(),
            addedAt = addedAt,
        )

        private fun BookEntity.toItem() = HomeItem(Medium.BOOK, bookKey, title, coverUrl = coverUrl, subtitle = authorList.firstOrNull(), addedAt = addedAt)

        private fun GameEntity.toCandidate() = TonightCandidate(
            Medium.GAME, igdbId.toString(), name,
            coverImageId = coverImageId,
            inProgress = gameStatus.isInProgress,
            addedAt = addedAt,
            quality = userRating?.let { it / 10.0 } ?: totalRating?.let { it / 100.0 },
        )

        private fun MovieEntity.toCandidate() = TonightCandidate(
            Medium.MOVIE, titleKey, title,
            coverUrl = TmdbImage.poster(posterUrl, width = 342),
            inProgress = watchStatus.isInProgress,
            addedAt = addedAt,
            quality = userRating?.let { it / 10.0 } ?: tmdbRating?.let { it / 10.0 },
            durationMinutes = runtimeMinutes,
            subtitle = year?.toString(),
        )

        private fun BookEntity.toCandidate() = TonightCandidate(
            Medium.BOOK, bookKey, title,
            coverUrl = coverUrl,
            inProgress = readStatus.isInProgress,
            addedAt = addedAt,
            quality = userRating?.let { it / 5.0 },
            durationMinutes = Tonight.readingMinutes(pageCount),
            subtitle = authorList.firstOrNull(),
        )
    }
}

class HomeViewModelFactory(
    private val games: BacklogRepository,
    private val movies: MovieRepository,
    private val books: BookRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == HomeViewModel::class.java)
        return HomeViewModel(games, movies, books) as T
    }
}
