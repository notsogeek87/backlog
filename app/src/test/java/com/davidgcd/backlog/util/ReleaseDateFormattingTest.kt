package com.davidgcd.backlog.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class ReleaseDateFormattingTest {

    private fun epochOf(year: Int, month: Int, day: Int): Long =
        java.time.LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().epochSecond

    @Test
    fun `isToday is true for the same UTC calendar day`() {
        val now = Instant.parse("2026-09-28T15:00:00Z")
        assertTrue(ReleaseDateFormatting.isToday(epochOf(2026, 9, 28), now))
        assertFalse(ReleaseDateFormatting.isToday(epochOf(2026, 9, 27), now))
    }

    @Test
    fun `isToday is false for a null date`() {
        assertFalse(ReleaseDateFormatting.isToday(null))
    }

    @Test
    fun `isReminderDueToday fires exactly leadDays before release`() {
        val now = Instant.parse("2026-09-21T09:00:00Z")
        val release = epochOf(2026, 9, 28)

        assertTrue(ReleaseDateFormatting.isReminderDueToday(release, leadDays = 7, now = now))
        assertFalse(ReleaseDateFormatting.isReminderDueToday(release, leadDays = 1, now = now))
        assertFalse(ReleaseDateFormatting.isReminderDueToday(release, leadDays = 0, now = now))
    }

    @Test
    fun `isReminderDueToday on release day itself uses leadDays zero`() {
        val now = Instant.parse("2026-09-28T09:00:00Z")
        val release = epochOf(2026, 9, 28)

        assertTrue(ReleaseDateFormatting.isReminderDueToday(release, leadDays = 0, now = now))
    }
}
