package com.davidgcd.backlog.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeToBeatTest {
    @Test
    fun `no figure means no data`() {
        assertFalse(TimeToBeat().hasData)
        assertFalse(TimeToBeat(normally = 0).hasData)
        assertTrue(TimeToBeat(completely = 3600).hasData)
    }

    @Test
    fun `best keeps the row with the most submissions and ignores empty ones`() {
        val rows = listOf(TimeToBeat(normally = 100, count = 3), TimeToBeat(normally = 200, count = 9), TimeToBeat(count = 50))
        assertEquals(200L, TimeToBeat.best(rows)?.normally)
        assertNull(TimeToBeat.best(listOf(TimeToBeat(count = 5))))
        assertNull(TimeToBeat.best(emptyList()))
    }
}
