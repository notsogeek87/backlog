package com.davidgcd.backlog.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davidgcd.backlog.BacklogApplication
import com.davidgcd.backlog.R
import com.davidgcd.backlog.util.ReleaseDateFormatting
import kotlinx.coroutines.flow.first

/**
 * Daily check: any active backlog game releasing today gets a local
 * notification. Equivalent role to the iOS app's release-day reminder
 * (NotificationService + SyncDriftDispatcher), trimmed to a single
 * "releases today" case for this first pass — no lead-time choice, no
 * drift/platform-change alerts yet (see README).
 */
class ReleaseReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BacklogApplication
        val preferences = NotificationPreferences(applicationContext)
        if (!preferences.releaseRemindersEnabled.first()) return Result.success()

        ensureChannel()

        val releasingToday = app.repository.activeGamesWithReleaseDate()
            .filter { ReleaseDateFormatting.isToday(it.firstReleaseDate) }

        if (releasingToday.isEmpty()) return Result.success()

        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission not granted (or pre-Android 13, where none is needed but we still
            // guard defensively): nothing more to do until the user grants it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return Result.success()
        }

        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        releasingToday.forEach { game ->
            val notification = NotificationCompat.Builder(applicationContext, NotificationIds.RELEASE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(game.name)
                .setContentText("Out today")
                .setAutoCancel(true)
                .build()
            notificationManager.notify(NotificationIds.releaseNotificationId(game.igdbId), notification)
        }

        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NotificationIds.RELEASE_CHANNEL_ID,
            "Release reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }
}
