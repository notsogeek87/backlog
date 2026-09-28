package com.davidgcd.backlog.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object ReleaseReminderScheduler {
    private const val WORK_NAME = "release_reminder_daily_check"

    /** Called once at launch — never overwrites a schedule the user has since customized. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReleaseReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Re-anchors the daily check to fire close to the user's chosen time —
     * WorkManager periodic work has no exact-time API, so this sets the
     * initial delay to the next occurrence of that time and lets the 24h
     * period keep it there.
     */
    fun reschedule(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val initialDelay = Duration.between(now, next)

        val request = PeriodicWorkRequestBuilder<ReleaseReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
