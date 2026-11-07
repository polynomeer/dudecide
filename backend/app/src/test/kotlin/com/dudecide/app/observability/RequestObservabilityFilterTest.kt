package com.dudecide.app.observability

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RequestObservabilityFilterTest {
    private val filter = RequestObservabilityFilter()

    @Test
    fun `generates a request id, exposes it in logs during the request, and clears it afterwards`() {
        var seenInMdc: String? = null
        val response = MockHttpServletResponse()

        filter.doFilter(MockHttpServletRequest("GET", "/api/v1/auth/me"), response, FilterChain { _, _ -> seenInMdc = MDC.get(RequestObservabilityFilter.MDC_KEY) })

        val header = response.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER)
        assertNotNull(header)
        assertEquals(header, seenInMdc)
        assertNull(MDC.get(RequestObservabilityFilter.MDC_KEY))
    }

    @Test
    fun `keeps a well-formed caller id and replaces one that could forge log content`() {
        val kept = MockHttpServletResponse()
        val goodRequest = MockHttpServletRequest("GET", "/x").apply { addHeader(RequestObservabilityFilter.REQUEST_ID_HEADER, "trace-1234") }
        filter.doFilter(goodRequest, kept, MockFilterChain())
        assertEquals("trace-1234", kept.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER))

        val replaced = MockHttpServletResponse()
        val forged = "abc12345\nERROR fake log line"
        val badRequest = MockHttpServletRequest("GET", "/x").apply { addHeader(RequestObservabilityFilter.REQUEST_ID_HEADER, forged) }
        filter.doFilter(badRequest, replaced, MockFilterChain())
        assertNotEquals(forged, replaced.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER))
    }

    @Test
    fun `rethrows unhandled failures after clearing the request id`() {
        val failing = FilterChain { _, _ -> throw IllegalStateException("boom") }

        assertThrows(IllegalStateException::class.java) {
            filter.doFilter(MockHttpServletRequest("POST", "/x"), MockHttpServletResponse(), failing)
        }
        assertNull(MDC.get(RequestObservabilityFilter.MDC_KEY))
    }
}
