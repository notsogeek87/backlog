package com.davidgcd.backlog.data.csv

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoExportFrequencyTest {
    @Test
    fun `stored name round-trips`() {
        AutoExportFrequency.entries.forEach {
            assertEquals(it, AutoExportFrequency.fromStoredName(it.name))
        }
    }

    @Test
    fun `unknown or missing name falls back to the default`() {
        assertEquals(AutoExportFrequency.DEFAULT, AutoExportFrequency.fromStoredName(null))
        assertEquals(AutoExportFrequency.DEFAULT, AutoExportFrequency.fromStoredName("HOURLY"))
    }

    @Test
    fun `repeat intervals are in days`() {
        assertEquals(1L, AutoExportFrequency.DAILY.days)
        assertEquals(7L, AutoExportFrequency.WEEKLY.days)
        assertEquals(30L, AutoExportFrequency.MONTHLY.days)
    }
}
