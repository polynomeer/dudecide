package com.dudecide.app.ratelimit

import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** In-memory fixed-window counter. Expired windows are swept once the map grows, so memory stays bounded. */
class FixedWindowRateLimiter(
    private val clock: Clock = Clock.systemUTC(),
    private val sweepThreshold: Int = 50_000,
) {
    private class Window(val endsAtMillis: Long, val count: Int)

    private val windows = ConcurrentHashMap<String, Window>()

    /** Counts one hit. Returns null when allowed, or how long to wait when the limit is exceeded. */
    fun acquire(key: String, limit: Int, window: Duration): Duration? {
        val now = clock.millis()
        if (windows.size > sweepThreshold) {
            windows.entries.removeIf { it.value.endsAtMillis <= now }
        }
        val current = windows.compute(key) { _, existing ->
            when {
                existing == null || existing.endsAtMillis <= now -> Window(now + window.toMillis(), 1)
                else -> Window(existing.endsAtMillis, existing.count + 1)
            }
        }!!
        return if (current.count <= limit) null else Duration.ofMillis(current.endsAtMillis - now)
    }
}
