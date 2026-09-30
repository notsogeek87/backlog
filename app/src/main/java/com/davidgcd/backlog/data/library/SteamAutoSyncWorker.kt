package com.davidgcd.backlog.data.library

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.davidgcd.backlog.BacklogApplication
import com.davidgcd.backlog.util.AppLogger
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Background Steam sync, so the library and wishlist stay current without opening Settings.
 * It runs the non-destructive half of the manual flow: [LibrarySyncService.sync] (refreshes playtime,
 * links certain matches) and [WishlistSyncService.sync]. Games not yet in the backlog are still left to
 * the manual import preview, where the user picks what to add.
 */
class SteamAutoSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BacklogApplication
        if (app.libraryAccountStore.get(LibraryProviders.STEAM) == null) return Result.success()

        var retry = false
        retry = runStep("library") { app.librarySyncService.sync(LibraryProviders.STEAM) } || retry
        retry = runStep("wishlist") { app.wishlistSyncService.sync() } || retry
        return if (retry) Result.retry() else Result.success()
    }

    /** @return true when the step failed in a way worth retrying later. */
    private suspend fun runStep(name: String, block: suspend () -> Unit): Boolean = try {
        block()
        false
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        val error = t.toLibraryException()
        AppLogger.network.warn("Auto Steam $name sync failed (${error.error}): ${error.message}")
        // A private profile or disconnected account won't fix itself: only retry transient errors.
        error.error == LibraryError.UNAVAILABLE || error.error == LibraryError.RATE_LIMITED
    }
}

object SteamAutoSyncScheduler {
    private const val WORK_NAME = "steam_auto_sync"

    /** Called once at launch; KEEP leaves the existing schedule alone. Runs about every 12 h on a network. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SteamAutoSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
