package com.dudecide.app.ratelimit

import com.dudecide.app.config.RateLimitProperties
import com.dudecide.app.domain.LocalActorProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class RateLimitInterceptorTest {

    private class MutableClock(var now: Instant = Instant.parse("2026-11-01T00:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    @Test
    fun `limiter blocks past the limit and resets with the next window`() {
        val clock = MutableClock()
        val limiter = FixedWindowRateLimiter(clock)
        val window = Duration.ofMinutes(1)

        assertNull(limiter.acquire("k", 2, window))
        assertNull(limiter.acquire("k", 2, window))
        assertNull(limiter.acquire("other", 2, window))
        assertEquals(window, limiter.acquire("k", 2, window))

        clock.now = clock.now.plusSeconds(61)
        assertNull(limiter.acquire("k", 2, window))
    }

    @Test
    fun `limiter sweeps expired windows once it grows`() {
        val clock = MutableClock()
        val limiter = FixedWindowRateLimiter(clock, sweepThreshold = 1)
        limiter.acquire("a", 1, Duration.ofSeconds(1))
        limiter.acquire("b", 1, Duration.ofSeconds(1))
        clock.now = clock.now.plusSeconds(2)

        assertNull(limiter.acquire("a", 1, Duration.ofSeconds(1)))
    }

    @Test
    fun `guest sessions are limited per address with a retry hint`() {
        val interceptor = interceptor(RateLimitProperties(guestSessionsPerHour = 1))

        assertTrue(interceptor.preHandle(post("/api/v1/auth/guest-sessions"), MockHttpServletResponse(), Any()))
        val blocked = MockHttpServletResponse()
        assertFalse(interceptor.preHandle(post("/api/v1/auth/guest-sessions"), blocked, Any()))
        assertEquals(429, blocked.status)
        assertNotNull(blocked.getHeader("Retry-After"))

        val otherClient = post("/api/v1/auth/guest-sessions").apply { remoteAddr = "10.0.0.2" }
        assertTrue(interceptor.preHandle(otherClient, MockHttpServletResponse(), Any()))
    }

    @Test
    fun `messages are limited per session user, not per address`() {
        val interceptor = interceptor(RateLimitProperties(messagesPerMinute = 1))
        val path = "/api/v1/threads/t-1/messages"

        assertTrue(interceptor.preHandle(postAs(path, "user-a"), MockHttpServletResponse(), Any()))
        assertFalse(interceptor.preHandle(postAs(path, "user-a"), MockHttpServletResponse(), Any()))
        assertTrue(interceptor.preHandle(postAs(path, "user-b"), MockHttpServletResponse(), Any()))
    }

    @Test
    fun `reads are never limited`() {
        val interceptor = interceptor(RateLimitProperties(messagesPerMinute = 0))
        val read = MockHttpServletRequest("GET", "/api/v1/threads/t-1/messages")

        assertTrue(interceptor.preHandle(read, MockHttpServletResponse(), Any()))
    }

    private fun interceptor(properties: RateLimitProperties) = RateLimitInterceptor(properties, FixedWindowRateLimiter(MutableClock()))

    private fun post(path: String) = MockHttpServletRequest("POST", path).apply { remoteAddr = "10.0.0.1" }

    private fun postAs(path: String, userId: String) = post(path).apply {
        getSession(true)!!.setAttribute(LocalActorProvider.SESSION_ACTOR_ID_KEY, userId)
    }
}
