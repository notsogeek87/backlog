package com.davidgcd.backlog.notifications

import com.davidgcd.backlog.model.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHistoryTest {

    private val now = 1_800_000_000_000L

    private fun entry(id: String, at: Long = now, kind: NotificationKind = NotificationKind.DEAL) = NotificationEntry(
        id = id,
        kind = kind,
        medium = Medium.GAME,
        itemKey = "42",
        title = "Hades",
        text = "En promotion sur Steam : -50 % (9,99 €)",
        postedAtMillis = at,
    )

    @Test
    fun `newest alert comes first`() {
        val list = NotificationHistory.add(listOf(entry("a", now - 1_000)), entry("b", now), now)
        assertEquals(listOf("b", "a"), list.map { it.id })
    }

    @Test
    fun `the same alert is only kept once`() {
        val first = NotificationHistory.add(emptyList(), entry("a", now - 1_000), now)
        val again = NotificationHistory.add(first, entry("a", now), now)
        assertEquals(1, again.size)
        assertEquals(now - 1_000, again.single().postedAtMillis)
    }

    @Test
    fun `entry id separates kind, item and detail`() {
        val deal = NotificationHistory.entryId(NotificationKind.DEAL, "42", "999")
        assertEquals(deal, NotificationHistory.entryId(NotificationKind.DEAL, "42", "999"))
        assertTrue(deal != NotificationHistory.entryId(NotificationKind.DEAL, "42", "499"))
        assertTrue(deal != NotificationHistory.entryId(NotificationKind.RELEASE, "42", "999"))
    }

    @Test
    fun `old alerts and overflow are pruned`() {
        val old = entry("old", now - NotificationHistory.MAX_AGE_MILLIS - 1)
        assertTrue(NotificationHistory.prune(listOf(old), now).isEmpty())

        val many = (0 until NotificationHistory.MAX_ENTRIES + 10).map { entry("e$it", now - it) }
        val pruned = NotificationHistory.prune(many, now)
        assertEquals(NotificationHistory.MAX_ENTRIES, pruned.size)
        assertEquals("e0", pruned.first().id)
    }

    @Test
    fun `unread counts alerts after the last visit`() {
        val list = listOf(entry("a", now), entry("b", now - 10), entry("c", now - 100))
        assertEquals(2, NotificationHistory.unreadCount(list, now - 50))
        assertEquals(0, NotificationHistory.unreadCount(list, now))
    }

    @Test
    fun `json round trip keeps every field`() {
        val list = listOf(
            entry("a", now, NotificationKind.RELEASE),
            entry("b", now - 5, NotificationKind.EPISODE).copy(medium = Medium.MOVIE, itemKey = "tv:1399", text = "Nouvel épisode S08E06 « The Iron Throne »"),
        )
        assertEquals(list, NotificationHistory.decode(NotificationHistory.encode(list)))
    }

    @Test
    fun `unreadable data is ignored, never fatal`() {
        assertTrue(NotificationHistory.decode(null).isEmpty())
        assertTrue(NotificationHistory.decode("pas du json").isEmpty())
        val mixed = """[{"id":"x","kind":"FUTURE_KIND","medium":"GAME","key":"1","title":"t","text":"t","at":1},""" +
            NotificationHistory.encode(listOf(entry("ok"))).removePrefix("[")
        assertEquals(listOf("ok"), NotificationHistory.decode(mixed).map { it.id })
    }
}
