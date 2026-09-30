package com.davidgcd.backlog.data.library.android

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import java.util.concurrent.TimeUnit

/**
 * Reads installed games from the [PackageManager]. Visibility comes from the `<queries>` LAUNCHER intent
 * in the manifest (no QUERY_ALL_PACKAGES needed). Usage figures need the special "usage access" grant.
 */
class PackageManagerInstalledApps(private val context: Context) : InstalledAppsSource {

    override fun installedGames(): List<InstalledGame> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName && it.isGame() }
            .map { InstalledGame(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION") // FLAG_IS_GAME still covers games predating the category attribute
    private fun ApplicationInfo.isGame() =
        category == ApplicationInfo.CATEGORY_GAME || flags and ApplicationInfo.FLAG_IS_GAME != 0

    override fun usageByPackage(): Map<String, AppUsage>? {
        if (!hasUsageAccess(context)) return null
        val stats = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // Android only keeps a limited history (up to ~a year): the total is "as far back as it goes".
        val total = stats.queryAndAggregateUsageStats(now - TimeUnit.DAYS.toMillis(365), now)
        val recent = stats.queryAndAggregateUsageStats(now - TimeUnit.DAYS.toMillis(14), now)
        return total.mapValues { (pkg, usage) ->
            AppUsage(
                totalMinutes = TimeUnit.MILLISECONDS.toMinutes(usage.totalTimeInForeground).toInt(),
                recentMinutes = TimeUnit.MILLISECONDS.toMinutes(recent[pkg]?.totalTimeInForeground ?: 0L).toInt(),
            )
        }
    }

    companion object {
        fun hasUsageAccess(context: Context): Boolean {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION") // checkOpNoThrow is the only variant available below API 29
            val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
