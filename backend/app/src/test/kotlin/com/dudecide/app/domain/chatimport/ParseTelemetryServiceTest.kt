package com.dudecide.app.domain.chatimport

import com.dudecide.app.api.dto.ParseTelemetryRequest
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParseTelemetryServiceTest {

    private val registry = SimpleMeterRegistry()
    private val service = ParseTelemetryService(registry)

    @Test
    fun `counts parse outcomes with bounded tags`() {
        service.record(ParseTelemetryRequest(format = "kakao_android", formatVersion = "android-ko-1", parsedCount = 10))
        service.record(ParseTelemetryRequest(format = "free_text", formatVersion = "free-text-named-1", parsedCount = 3))
        service.record(ParseTelemetryRequest(format = "<script>", formatVersion = "Minji's phone 010", parsedCount = 1, unparsedLineCount = 2))

        assertEquals(1.0, registry.counter("dudecide.import.parse", "format", "kakao_android", "formatVersion", "android-ko-1", "outcome", "parsed").count())
        assertEquals(1.0, registry.counter("dudecide.import.parse", "format", "free_text", "formatVersion", "free-text-named-1", "outcome", "fallback").count())
        assertEquals(1.0, registry.counter("dudecide.import.parse", "format", "other", "formatVersion", "other", "outcome", "partial").count())
    }

    @Test
    fun `accepts only line shapes as signatures`() {
        val signature = ParseTelemetryService.SIGNATURE

        assertTrue(signature.matches("DDDDH DDH DH H D:DD, H : DDH H?"))
        assertTrue(signature.matches("[W] [D:DD W] W W"))
        assertFalse(signature.matches("2026년 10월 1일 민지 : 18일 어때?"))
        assertFalse(signature.matches("[Minji] [9:00 PM] hi"))
        assertFalse(signature.matches(""))
    }
}
