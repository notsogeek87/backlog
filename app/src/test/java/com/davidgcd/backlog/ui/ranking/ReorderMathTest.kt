package com.davidgcd.backlog.ui.ranking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReorderMathTest {
    /** Three rows of 100 px stacked from 0. */
    private val rows = listOf(0 to 100, 100 to 100, 200 to 100)

    @Test
    fun `no swap until the dragged row passes the centre of its neighbour`() {
        assertNull(ReorderMath.swapTarget(rows, dragged = 0, dragOffset = 0f))
        assertNull(ReorderMath.swapTarget(rows, dragged = 0, dragOffset = 90f))
        assertEquals(1, ReorderMath.swapTarget(rows, dragged = 0, dragOffset = 110f))
    }

    @Test
    fun `dragging up swaps with the row above once past its centre`() {
        assertNull(ReorderMath.swapTarget(rows, dragged = 2, dragOffset = -90f))
        assertEquals(1, ReorderMath.swapTarget(rows, dragged = 2, dragOffset = -110f))
    }

    @Test
    fun `the offset is corrected by the neighbour size so the row stays under the finger`() {
        assertEquals(10f, ReorderMath.compensate(110f, targetSize = 100, movedDown = true), 0.001f)
        assertEquals(-10f, ReorderMath.compensate(-110f, targetSize = 100, movedDown = false), 0.001f)
    }

    @Test
    fun `an out of range dragged index is ignored`() {
        assertNull(ReorderMath.swapTarget(rows, dragged = 5, dragOffset = 0f))
    }
}
