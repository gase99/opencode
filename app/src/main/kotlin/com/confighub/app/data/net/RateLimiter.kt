package com.confighub.app.data.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-host rate limiter for source fetching — respects sources' rate limits and
 * avoids hammering the same host. The minimum interval applies per hostname.
 *
 * Hilt-friendly: no constructor parameters; tests may override [clock].
 */
@Singleton
class RateLimiter @Inject constructor() {

    /** Injectable clock for deterministic tests (production: wall clock). */
    internal var clock: () -> Long = System::currentTimeMillis

    private val mutex = Mutex()
    private val lastAttemptByHost = mutableMapOf<String, Long>()

    suspend fun <T> withPermit(host: String, minIntervalMs: Long, block: suspend () -> T): T {
        while (true) {
            val waitMs = mutex.withLock {
                val now = clock()
                val last = lastAttemptByHost[host] ?: 0L
                val wait = last + minIntervalMs - now
                if (wait <= 0) {
                    lastAttemptByHost[host] = now
                    null
                } else {
                    wait
                }
            }
            if (waitMs == null) return block()
            delay(waitMs)
        }
    }

    companion object {
        /** Default politeness interval per host (30 s). */
        const val DEFAULT_MIN_INTERVAL_MS = 30_000L
    }
}
