package com.dudecide.app.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID

/**
 * Gives every request an id for logs and responses, and logs server failures. Paths are logged as route templates:
 * raw paths carry invite tokens. Request and message bodies are never logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestObservabilityFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val requestId = request.getHeader(REQUEST_ID_HEADER)?.takeIf { SAFE_REQUEST_ID.matches(it) } ?: UUID.randomUUID().toString()
        val startedAt = System.nanoTime()
        MDC.put(MDC_KEY, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)
        try {
            filterChain.doFilter(request, response)
            if (response.status >= 500) {
                log.error("Request failed method={} route={} status={} durationMs={}", request.method, route(request), response.status, elapsedMillis(startedAt))
            }
        } catch (failure: Exception) {
            log.error("Request failed method={} route={} durationMs={}", request.method, route(request), elapsedMillis(startedAt), failure)
            throw failure
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    private fun route(request: HttpServletRequest): String =
        request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)?.toString() ?: "unmatched"

    private fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val MDC_KEY = "requestId"
        // A caller-supplied id ends up in logs, so anything that could forge log content is replaced.
        private val SAFE_REQUEST_ID = Regex("^[A-Za-z0-9-]{8,64}$")
    }
}
