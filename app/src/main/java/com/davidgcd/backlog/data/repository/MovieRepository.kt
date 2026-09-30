package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.imdb.ImdbClient
import com.davidgcd.backlog.data.local.MovieDao
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.local.genreList
import com.davidgcd.backlog.data.local.titleKind
import com.davidgcd.backlog.model.ImdbTitle
import com.davidgcd.backlog.model.MovieChart
import com.davidgcd.backlog.model.MovieRanking
import com.davidgcd.backlog.model.WatchStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for films & series: Room locally, IMDb for search / discovery / details —
 * the movie-side twin of [BacklogRepository].
 */
class MovieRepository(
    private val dao: MovieDao,
    private val imdb: ImdbClient,
) {
    /**
     * Titles seen in a search or a chart, kept for the session: opening one shows what the list
     * already knew even if IMDb's title page can't be read.
     */
    private val seen = ConcurrentHashMap<String, ImdbTitle>()

    fun observeAll(): Flow<List<MovieEntity>> = dao.observeAll()

    suspend fun allMovies(): List<MovieEntity> = dao.allMovies()

    fun observe(imdbId: String): Flow<MovieEntity?> = dao.observeById(imdbId)

    suspend fun find(imdbId: String): MovieEntity? = dao.findById(imdbId)

    suspend fun search(query: String): List<ImdbTitle> = imdb.search(query).also(::remember)

    suspend fun chart(chart: MovieChart): List<ImdbTitle> = imdb.chart(chart).also(::remember)

    /** Details from IMDb merged over what the lists already showed; null only if IMDb knows nothing of [imdbId]. */
    suspend fun fetchRemote(imdbId: String): ImdbTitle? {
        val known = seen[imdbId]
        val fresh = try {
            imdb.title(imdbId)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
        val merged = when {
            fresh == null -> known
            known == null -> fresh
            else -> fresh.copy(
                posterUrl = fresh.posterUrl ?: known.posterUrl,
                year = fresh.year ?: known.year,
                cast = fresh.cast ?: known.cast,
            )
        }
        merged?.let { seen[imdbId] = it }
        return merged
    }

    suspend fun add(title: ImdbTitle, status: WatchStatus = WatchStatus.TO_WATCH) {
        if (dao.findById(title.id) != null) return
        dao.upsert(title.toEntity(status))
    }

    suspend fun setArchived(entity: MovieEntity, archived: Boolean) = dao.update(entity.copy(isArchived = archived))

    suspend fun setStatus(entity: MovieEntity, status: WatchStatus) = dao.update(entity.copy(status = status.name))

    /** 1–10, or null to clear. */
    suspend fun setUserRating(entity: MovieEntity, rating: Int?) =
        dao.update(entity.copy(userRating = rating?.coerceIn(1, 10)))

    suspend fun applyRanking(newOrder: List<MovieEntity>) {
        MovieRanking.changes(newOrder).forEach { (id, rank) -> dao.setRank(id, rank) }
    }

    suspend fun remove(entity: MovieEntity) = dao.delete(entity)

    /** Pulls the latest rating / plot / poster of one saved title, keeping everything the user set. */
    suspend fun refresh(entity: MovieEntity) {
        val fresh = fetchRemote(entity.imdbId) ?: return
        val updated = entity.copy(
            title = fresh.title.ifBlank { entity.title },
            year = fresh.year ?: entity.year,
            releaseDate = fresh.releaseDate ?: entity.releaseDate,
            posterUrl = fresh.posterUrl ?: entity.posterUrl,
            genres = fresh.genres.takeIf { it.isNotEmpty() }?.joinToString(";") ?: entity.genres,
            plot = fresh.plot ?: entity.plot,
            imdbRating = fresh.rating ?: entity.imdbRating,
            runtimeMinutes = fresh.runtimeMinutes ?: entity.runtimeMinutes,
            directors = fresh.directors ?: entity.directors,
        )
        if (updated != entity) dao.update(updated)
    }

    /**
     * Imported rows (IMDb's CSV export) carry no poster. Fills a few at a time, gently: one small
     * request each, and a failure just leaves the poster for the next pass.
     */
    suspend fun backfillPosters(limit: Int = 12) {
        for (movie in dao.withoutPoster(limit)) {
            try {
                // "" = IMDb answered and has no poster: don't ask again on every pass.
                dao.setPoster(movie.imdbId, imdb.poster(movie.imdbId).orEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                return
            }
            delay(250)
        }
    }

    private fun remember(titles: List<ImdbTitle>) {
        titles.forEach { seen[it.id] = it }
    }
}

fun ImdbTitle.toEntity(status: WatchStatus = WatchStatus.TO_WATCH, userRating: Int? = null) = MovieEntity(
    imdbId = id,
    title = title,
    kind = kind.name,
    year = year,
    releaseDate = releaseDate,
    posterUrl = posterUrl,
    genres = genres.takeIf { it.isNotEmpty() }?.joinToString(";"),
    plot = plot,
    imdbRating = rating,
    runtimeMinutes = runtimeMinutes,
    directors = directors,
    status = status.name,
    userRating = userRating,
)

fun MovieEntity.toImdbTitle() = ImdbTitle(
    id = imdbId,
    title = title,
    kind = titleKind,
    year = year,
    releaseDate = releaseDate,
    posterUrl = posterUrl,
    genres = genreList,
    plot = plot,
    rating = imdbRating,
    runtimeMinutes = runtimeMinutes,
    directors = directors,
)
