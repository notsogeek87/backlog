package com.davidgcd.backlog.util

import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BacklogShareTextTest {
    private val labels = BacklogShareText.Labels(
        header = { "Mon backlog ($it jeux)" },
        status = { it.name },
        ranking = "MON CLASSEMENT",
    )

    @Test
    fun groupsByStatusSortsByNameAndSkipsArchivedAndEmptyGroups() {
        val games = listOf(
            GameEntity(igdbId = 1, name = "Zelda", status = GameStatus.BACKLOG.name),
            GameEntity(igdbId = 2, name = "Celeste", status = GameStatus.COMPLETED.name),
            GameEntity(igdbId = 3, name = "Alan Wake", status = GameStatus.BACKLOG.name),
            GameEntity(igdbId = 4, name = "Caché", status = GameStatus.PLAYED.name, isArchived = true),
        )
        assertEquals(
            "Mon backlog (3 jeux)\n\nBACKLOG (2)\n• Alan Wake\n• Zelda\n\nCOMPLETED (1)\n• Celeste",
            BacklogShareText.build(games, labels),
        )
    }

    @Test
    fun emptyBacklogIsJustTheHeader() {
        assertEquals("Mon backlog (0 jeux)", BacklogShareText.build(emptyList(), labels))
    }

    @Test
    fun appendsIgdbLinkWhenKnown() {
        val games = listOf(
            GameEntity(igdbId = 1, name = "Celeste"),
            GameEntity(igdbId = 2, name = "Hades"),
        )
        assertEquals(
            "Mon backlog (2 jeux)\n\nBACKLOG (2)\n• Celeste — https://www.igdb.com/games/celeste\n• Hades",
            BacklogShareText.build(games, labels, mapOf(1L to "https://www.igdb.com/games/celeste")),
        )
    }

    @Test
    fun rankedGamesLeadAsNumberedListAndLeaveTheirStatusGroup() {
        val games = listOf(
            GameEntity(igdbId = 1, name = "Zelda", status = GameStatus.BACKLOG.name),
            GameEntity(igdbId = 2, name = "Celeste", status = GameStatus.COMPLETED.name, userRank = 2),
            GameEntity(igdbId = 3, name = "Hades", status = GameStatus.PLAYED.name, userRank = 1),
            GameEntity(igdbId = 4, name = "Caché", userRank = 3, isArchived = true),
        )
        assertEquals(
            "Mon backlog (3 jeux)\n\nMON CLASSEMENT\n1. Hades (PLAYED) — https://www.igdb.com/games/hades\n2. Celeste (COMPLETED)" +
                "\n\nBACKLOG (1)\n• Zelda",
            BacklogShareText.build(games, labels, mapOf(3L to "https://www.igdb.com/games/hades")),
        )
    }
}
