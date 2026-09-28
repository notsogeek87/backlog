package com.davidgcd.backlog.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReleaseReminderScheduler {
    private const val WORK_NAME = "release_reminder_daily_check"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReleaseReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
