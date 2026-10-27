package com.davidgcd.backlog.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayTimeFormattingTest {
    @Test
    fun `under an hour shows minutes`() = assertEquals("45 min", PlayTimeFormatting.format(45 * 60L))

    @Test
    fun `whole hours`() = assertEquals("12 h", PlayTimeFormatting.format(12 * 3600L))

    @Test
    fun `half hours are kept under ten hours`() = assertEquals("7 h 30", PlayTimeFormatting.format(7 * 3600L + 1800))

    @Test
    fun `rounds to the nearest half hour and carries over`() = assertEquals("4 h", PlayTimeFormatting.format(3 * 3600L + 50 * 60))

    @Test
    fun `long durations round to whole hours`() = assertEquals("43 h", PlayTimeFormatting.format(42 * 3600L + 40 * 60))
}
