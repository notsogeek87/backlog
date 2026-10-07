package com.davidgcd.backlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleCacheTest {
    @Test
    fun `an entry is fresh inside the ttl and stale but still usable after it`() {
        var now = 1_000L
        val cache = StaleCache<String, Int>(ttlMillis = 100, clock = { now })
        assertNull(cache.get("a"))
        cache.put("a", 1)
        assertTrue(cache.get("a")!!.fresh)
        now += 150
        val stale = cache.get("a")!!
        assertFalse(stale.fresh)
        assertEquals(1, stale.value)
    }

    @Test
    fun `putting again restarts the clock`() {
        var now = 0L
        val cache = StaleCache<String, Int>(ttlMillis = 100, clock = { now })
        cache.put("a", 1)
        now = 150
        cache.put("a", 2)
        assertTrue(cache.get("a")!!.fresh)
        assertEquals(2, cache.get("a")!!.value)
    }
}
