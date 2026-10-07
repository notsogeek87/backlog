package com.davidgcd.backlog.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusFlowTest {
    @Test
    fun `a game moves forward one step at a time and stops once completed`() {
        assertEquals(GameStatus.PLAYED, GameStatus.BACKLOG.next())
        assertEquals(GameStatus.COMPLETED, GameStatus.PLAYED.next())
        assertNull(GameStatus.COMPLETED.next())
    }

    @Test
    fun `a wished game becomes an owned one`() {
        assertEquals(GameStatus.BACKLOG, GameStatus.WISHLIST.next())
    }

    @Test
    fun `films and books follow the same path`() {
        assertEquals(WatchStatus.WATCHING, WatchStatus.TO_WATCH.next())
        assertEquals(WatchStatus.WATCHED, WatchStatus.WATCHING.next())
        assertNull(WatchStatus.WATCHED.next())
        assertEquals(ReadStatus.READING, ReadStatus.TO_READ.next())
        assertEquals(ReadStatus.READ, ReadStatus.READING.next())
        assertNull(ReadStatus.READ.next())
        assertEquals(ReadStatus.TO_READ, ReadStatus.ABANDONED.next())
    }

    @Test
    fun `only the playing statuses count as in progress`() {
        assertEquals(listOf(GameStatus.PLAYED), GameStatus.entries.filter { it.isInProgress })
        assertEquals(listOf(WatchStatus.WATCHING), WatchStatus.entries.filter { it.isInProgress })
        assertEquals(listOf(ReadStatus.READING), ReadStatus.entries.filter { it.isInProgress })
    }

    @Test
    fun `finishing sets the date once and leaving finished clears it`() {
        assertEquals(100L, CompletionClock.next(null, finished = true, now = 100L))
        assertEquals(100L, CompletionClock.next(100L, finished = true, now = 999L))
        assertNull(CompletionClock.next(100L, finished = false, now = 999L))
    }
}
