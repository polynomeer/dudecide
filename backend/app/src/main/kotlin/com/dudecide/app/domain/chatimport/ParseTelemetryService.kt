package com.dudecide.app.domain.chatimport

import com.dudecide.app.api.dto.ParseTelemetryRequest
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Collects anonymous parser telemetry so export format changes show up before users report them.
 * Only line shapes are accepted: anything that could carry chat content is dropped, and tags are bounded.
 */
@Service
class ParseTelemetryService(
    private val meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun record(request: ParseTelemetryRequest) {
        val format = request.format.takeIf { it in FORMATS } ?: "other"
        val formatVersion = request.formatVersion?.takeIf { FORMAT_VERSION.matches(it) } ?: "other"
        val signatures = request.signatures.take(MAX_SIGNATURES).filter { SIGNATURE.matches(it) }
        val outcome = when {
            format == "free_text" -> "fallback"
            request.unparsedLineCount > 0 -> "partial"
            else -> "parsed"
        }

        meterRegistry.counter(
            "dudecide.import.parse",
            "format", format,
            "formatVersion", formatVersion,
            "outcome", outcome,
        ).increment()
        logger.info(
            "Chat import parse format={} formatVersion={} outcome={} parsedCount={} unparsedLineCount={} signatures={}",
            format,
            formatVersion,
            outcome,
            request.parsedCount.coerceAtLeast(0),
            request.unparsedLineCount.coerceAtLeast(0),
            signatures,
        )
    }

    internal companion object {
        const val MAX_SIGNATURES = 5
        val FORMATS = setOf("kakao_pc", "kakao_android", "kakao_ios", "kakao_csv", "free_text")
        val FORMAT_VERSION = Regex("^[a-z0-9-]{1,40}$")

        // D digits, H Hangul, W Latin letters, * anything else, plus separators seen in exports.
        val SIGNATURE = Regex("^[DHW* .,:;\\-\\[\\]()/~!?']{1,80}$")
    }
}
