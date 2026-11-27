package com.confighub.app.data.net

/** Injectable tuning for the fetch pipeline (defaults are production values). */
data class FetchPolicy(val minHostIntervalMs: Long = RateLimiter.DEFAULT_MIN_INTERVAL_MS)
