package com.davidgcd.backlog.data.library.android

import com.davidgcd.backlog.data.library.GameLibraryProvider
import com.davidgcd.backlog.data.library.LibraryGame
import com.davidgcd.backlog.data.library.LibraryProviders

/** One launchable app the device flags as a game. */
data class InstalledGame(val packageName: String, val label: String)

/** Foreground time of an app, in minutes. */
data class AppUsage(val totalMinutes: Int, val recentMinutes: Int)

/** What the provider needs from the device — a seam so the provider is testable without Android. */
interface InstalledAppsSource {
    fun installedGames(): List<InstalledGame>

    /** Null when the user hasn't granted usage access: playtime is then unknown, not zero. */
    fun usageByPackage(): Map<String, AppUsage>?
}

/**
 * The games installed on this phone. There is no account: the "library" is whatever the device says is
 * a game (`ApplicationInfo.CATEGORY_GAME`). The package name is the external id — it's also the `uid`
 * IGDB stores for its Android entries, so most games match without a title search.
 */
class AndroidLibraryProvider(private val apps: InstalledAppsSource) : GameLibraryProvider {
    override val id = LibraryProviders.ANDROID

    /** IGDB `external_game_sources`: 15 = Android (uid = package name). */
    override val igdbExternalSourceId = 15

    override suspend fun fetchLibrary(accountId: String): List<LibraryGame> {
        val usage = apps.usageByPackage()
        return apps.installedGames()
            .filter { it.label.isNotBlank() }
            .map {
                val figures = usage?.get(it.packageName)
                LibraryGame(
                    externalId = it.packageName,
                    name = it.label.trim(),
                    // No usage access → null ("no figure"); access but never opened → 0.
                    playtimeMinutes = if (usage == null) null else figures?.totalMinutes ?: 0,
                    recentPlaytimeMinutes = if (usage == null) null else figures?.recentMinutes ?: 0,
                )
            }
    }

    companion object {
        /** The single, implicit account of the device. */
        const val DEVICE_ACCOUNT_ID = "device"
    }
}
