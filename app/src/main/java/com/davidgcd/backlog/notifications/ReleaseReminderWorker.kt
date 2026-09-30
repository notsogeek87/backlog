package com.davidgcd.backlog.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davidgcd.backlog.BacklogApplication
import com.davidgcd.backlog.MainActivity
import com.davidgcd.backlog.R
import com.davidgcd.backlog.util.ReleaseDateFormatting
import kotlinx.coroutines.flow.first

/**
 * Daily check, equivalent role to the iOS app's release-day reminder
 * (NotificationService) plus its drift alerts (SyncDriftDispatcher):
 * 1. any active backlog game whose release date is exactly the user's
 *    chosen lead time away gets a local notification;
 * 2. if enabled, a bounded slice of the backlog is refreshed from IGDB and
 *    a date change / new platform posts its own alert — the entity is
 *    updated in the same pass, so a repeat run never re-reports it.
 */
class ReleaseReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BacklogApplication
        val preferences = NotificationPreferences(applicationContext)
        ensureChannels()

        val notificationsAllowed = ActivityCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

        if (preferences.releaseRemindersEnabled.first()) {
            checkReleaseReminders(app, preferences, notificationsAllowed)
        }

        if (preferences.dateChangeAlertsEnabled.first() || preferences.platformChangeAlertsEnabled.first()) {
            checkDrift(app, preferences, notificationsAllowed)
        }

        return Result.success()
    }

    private suspend fun checkReleaseReminders(
        app: BacklogApplication,
        preferences: NotificationPreferences,
        notificationsAllowed: Boolean,
    ) {
        val leadDays = preferences.schedule.first().leadDays
        val due = app.repository.activeGamesWithReleaseDate()
            .filter { ReleaseDateFormatting.isReminderDueToday(it.firstReleaseDate, leadDays) }

        if (due.isEmpty() || !notificationsAllowed) return

        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        val body = when (leadDays) {
            0 -> applicationContext.getString(R.string.notification_out_today)
            1 -> applicationContext.getString(R.string.notification_out_tomorrow)
            else -> applicationContext.getString(R.string.notification_out_in_days, leadDays)
        }
        due.forEach { game ->
            val notification = NotificationCompat.Builder(applicationContext, NotificationIds.RELEASE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(game.name)
                .setContentText(body)
                .setContentIntent(openGameIntent(game.igdbId))
                .setAutoCancel(true)
                .build()
            notificationManager.notify(NotificationIds.releaseNotificationId(game.igdbId), notification)
        }
    }

    private suspend fun checkDrift(
        app: BacklogApplication,
        preferences: NotificationPreferences,
        notificationsAllowed: Boolean,
    ) {
        val dateAlertsEnabled = preferences.dateChangeAlertsEnabled.first()
        val platformAlertsEnabled = preferences.platformChangeAlertsEnabled.first()
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

        app.repository.activeGames().take(DRIFT_CHECK_BATCH_SIZE).forEach { entity ->
            val drift = app.repository.refreshAndDetectDrift(entity) ?: return@forEach
            if (!notificationsAllowed) return@forEach

            if (drift.dateChanged && dateAlertsEnabled) {
                notificationManager.notify(
                    NotificationIds.dateChangeNotificationId(entity.igdbId),
                    driftNotification(entity.igdbId, entity.name, applicationContext.getString(R.string.notification_date_changed)),
                )
            }
            if (drift.newPlatforms.isNotEmpty() && platformAlertsEnabled) {
                val platforms = drift.newPlatforms.joinToString(", ")
                notificationManager.notify(
                    NotificationIds.newPlatformNotificationId(entity.igdbId),
                    driftNotification(entity.igdbId, entity.name, applicationContext.getString(R.string.notification_new_platforms, platforms)),
                )
            }
        }
    }

    private fun driftNotification(igdbId: Long, title: String, body: String) =
        NotificationCompat.Builder(applicationContext, NotificationIds.DRIFT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openGameIntent(igdbId))
            .setAutoCancel(true)
            .build()

    /** Tapping a notification opens the app on that game's detail screen. */
    private fun openGameIntent(igdbId: Long): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(NotificationIds.EXTRA_GAME_ID, igdbId)
        // A distinct request code per game keeps each PendingIntent's extras separate (extras aren't part of intent equality).
        return PendingIntent.getActivity(
            applicationContext,
            igdbId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationIds.RELEASE_CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_release),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationIds.DRIFT_CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_drift),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private companion object {
        /** Bounds how many games get an IGDB refresh per run — a large backlog shouldn't hammer the API daily. */
        const val DRIFT_CHECK_BATCH_SIZE = 30
    }
}
