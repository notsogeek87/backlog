package com.davidgcd.backlog.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PersonalRankingTest {
    private data class Item(val key: String, val rank: Int?, val addedAt: Long)

    private val a = Item("a", rank = 2, addedAt = 30)
    private val b = Item("b", rank = 1, addedAt = 20)
    private val c = Item("c", rank = null, addedAt = 10)
    private val d = Item("d", rank = null, addedAt = 5)

    private fun order(items: List<Item>) = PersonalRanking.order(items, { it.rank }, { it.addedAt })

    @Test
    fun `ranked items come first by rank, then the unranked ones by date added`() {
        assertEquals(listOf(b, a, d, c), order(listOf(a, b, c, d)))
    }

    @Test
    fun `equal ranks fall back on the date added`() {
        val e = Item("e", rank = 1, addedAt = 1)
        assertEquals(listOf(e, b), order(listOf(b, e)))
    }

    @Test
    fun `moveTo moves an item and leaves the list untouched when nothing moves`() {
        val list = listOf(a, b, c)
        assertEquals(listOf(c, a, b), PersonalRanking.moveTo(list, 2, 0))
        assertSame(list, PersonalRanking.moveTo(list, 1, 1))
        assertSame(list, PersonalRanking.moveTo(list, 0, 5))
        assertSame(list, PersonalRanking.move(list, 0, -1))
        assertEquals(listOf(b, a, c), PersonalRanking.move(list, 0, 1))
    }

    @Test
    fun `changes only lists the items whose rank differs`() {
        // New order: b stays 1, a stays 2, c becomes 3.
        assertEquals(mapOf("c" to 3), PersonalRanking.changes(listOf(b, a, c), { it.rank }, { it.key }))
    }
}
