package com.davidgcd.backlog.data.csv

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.davidgcd.backlog.BacklogApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Periodic export of the whole backlog to the folder the user picked; failures are recorded, not retried blindly. */
class AutoExportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BacklogApplication
        val preferences = AutoExportPreferences(applicationContext)
        if (!preferences.enabled.first()) return Result.success()
        val folder = preferences.folderUri.first()?.let(Uri::parse) ?: return Result.success()

        val succeeded = try {
            if (!AutoExportFolder.hasPermission(applicationContext, folder)) {
                false
            } else {
                withContext(Dispatchers.IO) {
                    val target = AutoExportFolder.exportFileUri(applicationContext, folder)
                    app.csvExportService.export(target, app.repository.allGames())
                }
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        preferences.recordResult(succeeded)
        // The next period retries; a lost folder needs the user, so WorkManager backoff wouldn't help.
        return Result.success()
    }
}

object AutoExportScheduler {
    private const val WORK_NAME = "auto_export_csv"

    /** (Re)starts the periodic export; a changed frequency replaces the previous schedule. */
    fun schedule(context: Context, frequency: AutoExportFrequency) {
        val request = PeriodicWorkRequestBuilder<AutoExportWorker>(frequency.days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
