package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.tmdb.TmdbClient
import com.davidgcd.backlog.data.tmdb.TmdbException
import com.davidgcd.backlog.data.tmdb.TmdbUserList
import com.davidgcd.backlog.data.local.MovieDao
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.local.genreList
import com.davidgcd.backlog.data.local.titleKind
import com.davidgcd.backlog.model.CastMember
import com.davidgcd.backlog.model.CompletionClock
import com.davidgcd.backlog.model.PersonFilmography
import com.davidgcd.backlog.model.MediaTitle
import com.davidgcd.backlog.model.MovieChart
import com.davidgcd.backlog.model.MovieRanking
import com.davidgcd.backlog.model.Trailer
import com.davidgcd.backlog.model.WatchProviders
import com.davidgcd.backlog.model.WatchStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

/** A list of the user's TMDB account and whether the title being looked at is in it. */
data class TmdbListEntry(val list: TmdbUserList, val contains: Boolean)

/** The watchlist plus the user's own lists, for one title. */
data class TmdbListsState(val watchlist: Boolean, val lists: List<TmdbListEntry>)

/**
 * Single source of truth for films & series: Room locally, TMDB for search / discovery / details —
 * the movie-side twin of [BacklogRepository].
 */
class MovieRepository(
    private val dao: MovieDao,
    private val tmdb: TmdbClient,
) {
    /**
     * Titles seen in a search or a chart, kept for the session: opening one shows what the list
     * already knew even if TMDB's title page can't be read.
     */
    private val seen = ConcurrentHashMap<String, MediaTitle>()

    fun observeAll(): Flow<List<MovieEntity>> = dao.observeAll()

    suspend fun allMovies(): List<MovieEntity> = dao.allMovies()

    fun observe(titleKey: String): Flow<MovieEntity?> = dao.observeById(titleKey)

    suspend fun find(titleKey: String): MovieEntity? = dao.findById(titleKey)

    suspend fun search(query: String): List<MediaTitle> = tmdb.search(query).also(::remember)

    suspend fun chart(chart: MovieChart): List<MediaTitle> = tmdb.chart(chart).also(::remember)

    /** Details from TMDB merged over what the lists already showed; null only if TMDB knows nothing of [titleKey]. */
    suspend fun fetchRemote(titleKey: String): MediaTitle? {
        val known = seen[titleKey]
        val fresh = try {
            tmdb.details(titleKey)
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
        merged?.let { seen[titleKey] = it }
        return merged
    }

    /** Live photos of the director and actors; never cached. */
    suspend fun credits(titleKey: String): List<CastMember> = tmdb.credits(titleKey)

    /** Live filmography of a person (acting, or directing); never cached. */
    suspend fun personFilmography(personId: Long, asDirector: Boolean): PersonFilmography? =
        tmdb.personFilmography(personId, asDirector)

    /** Live "where to watch" for France; never cached, since availability changes. */
    suspend fun watchProviders(titleKey: String): WatchProviders? = tmdb.watchProviders(titleKey)

    /** Live trailer (French when possible); never cached. */
    suspend fun trailer(titleKey: String): Trailer? = tmdb.trailer(titleKey)

    /** Last aired / next episode of a series (for the « nouvel épisode » alert); null for a film or when TMDB can't say. */
    suspend fun episodes(titleKey: String): com.davidgcd.backlog.data.tmdb.SeriesEpisodes? = tmdb.episodes(titleKey)

    suspend fun add(title: MediaTitle, status: WatchStatus = WatchStatus.TO_WATCH) {
        if (dao.findById(title.id) != null) return
        dao.upsert(title.toEntity(status))
        // A new "to watch" title also goes to the user's TMDB watchlist (when an account is linked).
        if (status == WatchStatus.TO_WATCH) bestEffort { tmdb.setWatchlist(title.id, true) }
    }

    suspend fun setArchived(entity: MovieEntity, archived: Boolean) = dao.update(entity.copy(isArchived = archived))

    suspend fun setStatus(entity: MovieEntity, status: WatchStatus) {
        dao.update(entity.copy(status = status.name, completedAt = CompletionClock.next(entity.completedAt, status == WatchStatus.WATCHED)))
        // The TMDB watchlist mirrors "to watch": entering it adds the title, leaving it removes it.
        if (status == WatchStatus.TO_WATCH) bestEffort { tmdb.setWatchlist(entity.titleKey, true) }
        else if (entity.status == WatchStatus.TO_WATCH.name) bestEffort { tmdb.setWatchlist(entity.titleKey, false) }
    }

    /** 1–10, or null to clear. */
    suspend fun setUserRating(entity: MovieEntity, rating: Int?) {
        val value = rating?.coerceIn(1, 10)
        dao.update(entity.copy(userRating = value, ratedAt = value?.let { System.currentTimeMillis() }))
        // Mirrors the rating to the user's TMDB account when one is linked; offline / signed out just keeps it local.
        bestEffort { tmdb.rate(entity.titleKey, value) }
    }

    suspend fun applyRanking(newOrder: List<MovieEntity>) {
        MovieRanking.changes(newOrder).forEach { (id, rank) -> dao.setRank(id, rank) }
    }

    suspend fun remove(entity: MovieEntity) = dao.delete(entity)

    /** Puts back a title removed a moment ago (the « Annuler » of a deletion), exactly as it was. */
    suspend fun restore(entity: MovieEntity) = dao.upsert(entity)

    /** Pulls the latest rating / plot / poster of one saved title, keeping everything the user set. */
    suspend fun refresh(entity: MovieEntity) {
        val fresh = fetchRemote(entity.titleKey) ?: return
        val updated = entity.copy(
            title = fresh.title.ifBlank { entity.title },
            year = fresh.year ?: entity.year,
            releaseDate = fresh.releaseDate ?: entity.releaseDate,
            posterUrl = fresh.posterUrl ?: entity.posterUrl,
            genres = fresh.genres.takeIf { it.isNotEmpty() }?.joinToString(";") ?: entity.genres,
            plot = fresh.plot ?: entity.plot,
            tmdbRating = fresh.rating ?: entity.tmdbRating,
            runtimeMinutes = fresh.runtimeMinutes ?: entity.runtimeMinutes,
            directors = fresh.directors ?: entity.directors,
            cast = fresh.cast ?: entity.cast,
        )
        if (updated != entity) dao.update(updated)
    }

    /**
     * Where [titleKey] stands in the user's TMDB account: the watchlist and each list they created, with whether
     * the title is in it. Null when no account is linked or TMDB can't be read (the card is then hidden).
     */
    suspend fun tmdbLists(titleKey: String): TmdbListsState? {
        if (tmdb.currentSession() == null) return null
        return try {
            coroutineScope {
                val watchlist = async { tmdb.isInWatchlist(titleKey) }
                val lists = tmdb.userLists().map { list ->
                    // One list that can't be checked must not hide the others: it shows as « not in it ».
                    async { TmdbListEntry(list, try { tmdb.isInList(list.id, titleKey) } catch (e: TmdbException) { false }) }
                }
                TmdbListsState(watchlist.await(), lists.awaitAll())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TmdbException) {
            null
        }
    }

    /** Adds / removes the title from the TMDB watchlist; throws [TmdbException] so the screen can say it failed. */
    suspend fun setInTmdbWatchlist(titleKey: String, on: Boolean) = tmdb.setWatchlist(titleKey, on)

    /** Adds / removes the title from one of the user's TMDB lists; throws [TmdbException] on failure. */
    suspend fun setInTmdbList(listId: Long, titleKey: String, on: Boolean) = tmdb.setInList(listId, titleKey, on)

    /** Account write-backs never fail the local action: no session, no network or a TMDB hiccup is silently skipped. */
    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            if (tmdb.currentSession() != null) block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Local data is the source of truth; the next import reconciles.
        }
    }

    private fun remember(titles: List<MediaTitle>) {
        titles.forEach { seen[it.id] = it }
    }
}

fun MediaTitle.toEntity(status: WatchStatus = WatchStatus.TO_WATCH, userRating: Int? = null) = MovieEntity(
    titleKey = id,
    title = title,
    kind = kind.name,
    year = year,
    releaseDate = releaseDate,
    posterUrl = posterUrl,
    genres = genres.takeIf { it.isNotEmpty() }?.joinToString(";"),
    plot = plot,
    tmdbRating = rating,
    runtimeMinutes = runtimeMinutes,
    directors = directors,
    cast = cast,
    status = status.name,
    userRating = userRating,
    ratedAt = userRating?.let { System.currentTimeMillis() },
)

fun MovieEntity.toMediaTitle() = MediaTitle(
    id = titleKey,
    title = title,
    kind = titleKind,
    year = year,
    releaseDate = releaseDate,
    posterUrl = posterUrl,
    genres = genreList,
    plot = plot,
    rating = tmdbRating,
    runtimeMinutes = runtimeMinutes,
    directors = directors,
    cast = cast,
)
