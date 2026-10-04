package com.davidgcd.backlog.ui.whatsnew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
    private val all = listOf(ChangelogEntry(1, "a", ""), ChangelogEntry(2, "b", ""), ChangelogEntry(3, "c", ""))

    @Test
    fun `only entries newer than the last seen one, newest first`() {
        assertEquals(listOf(3, 2), Changelog.since(1, all).map { it.id })
        assertTrue(Changelog.since(3, all).isEmpty())
    }

    @Test
    fun `ids are unique and increasing`() {
        val ids = Changelog.entries.map { it.id }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.size, ids.toSet().size)
    }
}
