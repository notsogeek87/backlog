package com.davidgcd.backlog.data.imdb

import com.davidgcd.backlog.data.library.LibraryAccountStore
import com.davidgcd.backlog.data.library.LibraryProviders
import com.davidgcd.backlog.data.local.MovieDao
import com.davidgcd.backlog.data.local.watchStatus
import com.davidgcd.backlog.data.repository.toEntity
import com.davidgcd.backlog.model.WatchStatus

/** What one import did — the figures the import screen and the Settings card show. */
data class ImdbSyncResult(
    val watchlistCount: Int,
    val ratedCount: Int,
    val added: Int,
    val updated: Int,
    /** False when the watchlist page could not be located (ratings were still imported). */
    val watchlistFound: Boolean,
)

/**
 * Brings the signed-in user's IMDb account into the app: their ratings (a rated title is a watched
 * one) and their watchlist (to watch). Only ever adds titles and fills what the user hasn't set here —
 * a status, rank or rating changed in the app is never overwritten, and nothing is deleted.
 */
class ImdbSyncService(
    private val client: ImdbClient,
    private val dao: MovieDao,
    private val accounts: LibraryAccountStore,
) {
    /** The `ur…` id of the current IMDb session, or null if signed out. */
    suspend fun currentUserId(): String? = client.resolveUserId()

    suspend fun sync(now: Long = System.currentTimeMillis()): ImdbSyncResult {
        val userId = client.resolveUserId() ?: throw ImdbException(ImdbError.NOT_SIGNED_IN)

        val rated = ImdbCsv.parse(client.ratingsCsv(userId))
        val watchlistCsv = client.watchlistCsv(userId)
        val watchlist = watchlistCsv?.let(ImdbCsv::parse).orEmpty()

        var added = 0
        var updated = 0
        val ratedIds = rated.mapTo(HashSet()) { it.title.id }

        suspend fun upsertTitle(title: com.davidgcd.backlog.model.ImdbTitle, status: WatchStatus, userRating: Int?) {
            val existing = dao.findById(title.id)
            if (existing == null) {
                dao.upsert(title.toEntity(status, userRating))
                added++
                return
            }
            var next = existing
            if (userRating != null && existing.userRating != userRating) next = next.copy(userRating = userRating)
            // Rating something on IMDb means it was seen; never demote, and never override a status set here.
            if (status == WatchStatus.WATCHED && existing.watchStatus == WatchStatus.TO_WATCH) next = next.copy(status = WatchStatus.WATCHED.name)
            if (next != existing) {
                dao.update(next)
                updated++
            }
        }

        rated.forEach { upsertTitle(it.title, WatchStatus.WATCHED, it.userRating) }
        watchlist.filter { it.title.id !in ratedIds }.forEach { upsertTitle(it.title, WatchStatus.TO_WATCH, null) }

        val total = ratedIds.size + watchlist.count { it.title.id !in ratedIds }
        accounts.recordSync(LibraryProviders.IMDB, now, total, total - added, added)
        return ImdbSyncResult(
            watchlistCount = watchlist.size,
            ratedCount = rated.size,
            added = added,
            updated = updated,
            watchlistFound = watchlistCsv != null,
        )
    }
}
