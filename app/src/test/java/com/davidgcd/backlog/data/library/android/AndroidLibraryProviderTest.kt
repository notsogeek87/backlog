package com.davidgcd.backlog.data.library.android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidLibraryProviderTest {
    private class FakeApps(
        var games: List<InstalledGame> = emptyList(),
        var usage: Map<String, AppUsage>? = null,
    ) : InstalledAppsSource {
        override fun installedGames() = games
        override fun usageByPackage() = usage
    }

    private val apps = FakeApps()
    private val provider = AndroidLibraryProvider(apps)

    @Test
    fun `uses the package name as external id and targets IGDB's Android source`() = runTest {
        apps.games = listOf(InstalledGame("com.mojang.minecraftpe", " Minecraft "))

        val games = provider.fetchLibrary(AndroidLibraryProvider.DEVICE_ACCOUNT_ID)

        assertEquals(1, games.size)
        assertEquals("com.mojang.minecraftpe", games[0].externalId)
        assertEquals("Minecraft", games[0].name)
        assertEquals(15, provider.igdbExternalSourceId)
    }

    @Test
    fun `without usage access playtime is unknown, not zero`() = runTest {
        apps.games = listOf(InstalledGame("com.a", "A"))
        apps.usage = null

        val game = provider.fetchLibrary("device").single()

        assertNull(game.playtimeMinutes)
        assertNull(game.recentPlaytimeMinutes)
    }

    @Test
    fun `with usage access an app never opened is zero and others carry their figures`() = runTest {
        apps.games = listOf(InstalledGame("com.a", "A"), InstalledGame("com.b", "B"))
        apps.usage = mapOf("com.a" to AppUsage(totalMinutes = 300, recentMinutes = 45))

        val (a, b) = provider.fetchLibrary("device")

        assertEquals(300, a.playtimeMinutes)
        assertEquals(45, a.recentPlaytimeMinutes)
        assertEquals(0, b.playtimeMinutes)
        assertEquals(0, b.recentPlaytimeMinutes)
    }

    @Test
    fun `apps without a label are skipped`() = runTest {
        apps.games = listOf(InstalledGame("com.a", "  "), InstalledGame("com.b", "B"))

        assertEquals(listOf("com.b"), provider.fetchLibrary("device").map { it.externalId })
    }
}
