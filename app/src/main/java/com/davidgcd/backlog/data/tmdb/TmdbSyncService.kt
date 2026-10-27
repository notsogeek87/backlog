package com.davidgcd.backlog.data.tmdb

import com.davidgcd.backlog.data.library.LibraryAccountStore
import com.davidgcd.backlog.data.library.LibraryProviders
import com.davidgcd.backlog.data.local.MovieDao
import com.davidgcd.backlog.data.local.watchStatus
import com.davidgcd.backlog.data.repository.toEntity
import com.davidgcd.backlog.model.MediaTitle
import com.davidgcd.backlog.model.WatchStatus

/** What one import did — the figures the import screen shows. */
data class TmdbSyncResult(
    val watchlistCount: Int,
    val ratedCount: Int,
    val added: Int,
    val updated: Int,
    /** Titles of the app sent to the account on its first sync: ratings, then « to watch » ones. */
    val uploaded: Int = 0,
)

/**
 * Brings the signed-in user's TMDB account into the app: their ratings (a rated title is a watched
 * one) and their watchlist (to watch). Only ever adds titles and fills what the user hasn't set here —
 * a status, rank or rating changed in the app is never overwritten, and nothing is deleted.
 *
 * The first sync of an account also goes the other way: what was added in the app before linking it
 * (later changes are mirrored one by one by `MovieRepository`) is sent once — ratings TMDB lacks, and
 * « to watch » titles missing from the watchlist. Only once, so a title the user later removed on
 * themoviedb.org is not pushed back on every sync.
 */
class TmdbSyncService(
    private val client: TmdbClient,
    private val dao: MovieDao,
    private val accounts: LibraryAccountStore,
) {
    val isConfigured: Boolean get() = client.isConfigured

    /** Step 1 of the sign-in: the TMDB approval page to open, and the token to finish with afterwards. */
    suspend fun startLogin(redirectTo: String): Pair<String, String> {
        val token = client.newRequestToken()
        return token to client.approvalUrl(token, redirectTo)
    }

    /** Step 3: the user approved [requestToken]; open the session and link the account. */
    suspend fun finishLogin(requestToken: String) {
        val session = client.createSession(requestToken)
        accounts.connect(LibraryProviders.TMDB, session.accountId.toString(), session.username)
    }

    suspend fun signOut() = client.signOut()

    suspend fun sync(now: Long = System.currentTimeMillis()): TmdbSyncResult {
        val rated = client.rated()
        val watchlist = client.watchlist()

        var added = 0
        var updated = 0
        val ratedIds = rated.mapTo(HashSet()) { it.title.id }

        suspend fun upsertTitle(title: MediaTitle, status: WatchStatus, userRating: Int?) {
            val existing = dao.findById(title.id)
            if (existing == null) {
                dao.upsert(title.toEntity(status, userRating))
                added++
                return
            }
            var next = existing
            if (userRating != null && existing.userRating != userRating) next = next.copy(userRating = userRating, ratedAt = now)
            // Rating something on TMDB means it was seen; never demote, and never override a status set here.
            if (status == WatchStatus.WATCHED && existing.watchStatus == WatchStatus.TO_WATCH) next = next.copy(status = WatchStatus.WATCHED.name)
            if (next != existing) {
                dao.update(next)
                updated++
            }
        }

        rated.forEach { upsertTitle(it.title, WatchStatus.WATCHED, it.userRating) }
        watchlist.filter { it.title.id !in ratedIds }.forEach { upsertTitle(it.title, WatchStatus.TO_WATCH, null) }

        val total = ratedIds.size + watchlist.count { it.title.id !in ratedIds }
        accounts.recordSync(LibraryProviders.TMDB, now, total, total - added, added)
        val uploaded = if (accounts.get(LibraryProviders.TMDB)?.localUploadedAt == null) {
            uploadLocal(ratedIds, watchlist.mapTo(HashSet()) { it.title.id }).also { accounts.recordLocalUpload(LibraryProviders.TMDB, now) }
        } else 0
        return TmdbSyncResult(watchlistCount = watchlist.size, ratedCount = rated.size, added = added, updated = updated, uploaded = uploaded)
    }

    /**
     * Sends the app's titles the account doesn't have yet. A failure stops here without marking the
     * upload done: the next sync starts it again (rating or adding to the watchlist twice is harmless).
     */
    private suspend fun uploadLocal(ratedIds: Set<String>, watchlistIds: Set<String>): Int {
        var sent = 0
        dao.allMovies().filterNot { it.isArchived }.forEach { movie ->
            val rating = movie.userRating
            when {
                rating != null && movie.titleKey !in ratedIds -> client.rate(movie.titleKey, rating)
                rating == null && movie.watchStatus == WatchStatus.TO_WATCH && movie.titleKey !in watchlistIds &&
                    movie.titleKey !in ratedIds -> client.setWatchlist(movie.titleKey, true)
                else -> return@forEach
            }
            sent++
        }
        return sent
    }
}
