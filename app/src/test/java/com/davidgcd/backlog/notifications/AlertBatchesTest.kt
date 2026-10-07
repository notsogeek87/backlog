package com.davidgcd.backlog.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertBatchesTest {
    private val items = (1..10).toList()

    @Test
    fun `a short list is taken whole`() {
        assertEquals(items, AlertBatches.slice(items, 25, 3))
    }

    @Test
    fun `a long list is covered page by page, one page per day, then starts over`() {
        assertEquals(listOf(1, 2, 3, 4), AlertBatches.slice(items, 4, 0))
        assertEquals(listOf(5, 6, 7, 8), AlertBatches.slice(items, 4, 1))
        assertEquals(listOf(9, 10), AlertBatches.slice(items, 4, 2))
        assertEquals(listOf(1, 2, 3, 4), AlertBatches.slice(items, 4, 3))
    }
}
