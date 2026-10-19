package com.dudecide.app.api

import com.dudecide.app.api.dto.ParseTelemetryRequest
import com.dudecide.app.domain.chatimport.ParseTelemetryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/telemetry")
@Tag(name = "Telemetry")
class TelemetryController(
    private val parseTelemetryService: ParseTelemetryService,
) {

    @PostMapping("/import-parse")
    @Operation(summary = "Report anonymous chat export parse results (line shapes only)")
    fun recordImportParse(@RequestBody request: ParseTelemetryRequest): ResponseEntity<Void> {
        parseTelemetryService.record(request)
        return ResponseEntity.noContent().build()
    }
}
