package com.dudecide.app.ratelimit

import com.dudecide.app.config.RateLimitProperties
import com.dudecide.app.domain.LocalActorProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration

/**
 * Throttles the write endpoints that are cheap to abuse. Runs as an MVC interceptor rather than a servlet filter
 * so 429 responses keep their CORS headers and browsers can read them.
 */
class RateLimitInterceptor(
    private val properties: RateLimitProperties,
    private val limiter: FixedWindowRateLimiter,
) : HandlerInterceptor {

    private data class Rule(val name: String, val limit: Int, val window: Duration, val client: String)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method != HttpMethod.POST.name()) return true
        val rule = ruleFor(request) ?: return true
        val retryAfter = limiter.acquire("${rule.name}:${rule.client}", rule.limit, rule.window) ?: return true

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader(HttpHeaders.RETRY_AFTER, (retryAfter.toSeconds() + 1).toString())
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write("""{"status":429,"error":"Too Many Requests","limit":"${rule.name}"}""")
        return false
    }

    private fun ruleFor(request: HttpServletRequest): Rule? {
        val path = request.requestURI
        val address = request.remoteAddr
        return when {
            path.endsWith("/auth/guest-sessions") -> Rule("guest-sessions", properties.guestSessionsPerHour, HOUR, address)
            path.startsWith(INVITE_PREFIX) && path.endsWith("/join") -> Rule("invite-joins", properties.inviteJoinsPerHour, HOUR, address)
            path.endsWith("/telemetry/import-parse") -> Rule("telemetry", properties.telemetryPerHour, HOUR, address)
            path.startsWith(THREAD_PREFIX) && path.endsWith("/messages") -> Rule(
                "messages",
                properties.messagesPerMinute,
                MINUTE,
                request.getSession(false)?.getAttribute(LocalActorProvider.SESSION_ACTOR_ID_KEY)?.toString() ?: address,
            )
            else -> null
        }
    }

    companion object {
        private val HOUR = Duration.ofHours(1)
        private val MINUTE = Duration.ofMinutes(1)
        private const val INVITE_PREFIX = "/api/v1/invite-links/"
        private const val THREAD_PREFIX = "/api/v1/threads/"

        val PATH_PATTERNS = listOf(
            "/api/v1/auth/guest-sessions",
            "/api/v1/invite-links/*/join",
            "/api/v1/telemetry/import-parse",
            "/api/v1/threads/*/messages",
        )
    }
}
