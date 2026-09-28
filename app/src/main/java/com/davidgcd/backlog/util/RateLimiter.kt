package com.davidgcd.backlog.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sliding-window request throttle, mirroring the iOS app's RateLimiter
 * (IGDB: 4 req/s). The slot is reserved before the request is sent.
 */
class RateLimiter(private val maxRequests: Int, private val perMillis: Long) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>()

    suspend fun acquire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            while (timestamps.isNotEmpty() && now - timestamps.first() > perMillis) {
                timestamps.removeFirst()
            }
            if (timestamps.size >= maxRequests) {
                val waitMillis = perMillis - (now - timestamps.first())
                if (waitMillis > 0) delay(waitMillis)
            }
            timestamps.addLast(System.currentTimeMillis())
        }
    }

    companion object {
        val igdb = RateLimiter(maxRequests = 4, perMillis = 1_000)
    }
}
