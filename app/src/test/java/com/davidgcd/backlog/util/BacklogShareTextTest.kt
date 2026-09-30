package com.davidgcd.backlog.util

import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BacklogShareTextTest {
    private val labels = BacklogShareText.Labels(
        header = { "Mon backlog ($it jeux)" },
        status = { it.name },
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
}
