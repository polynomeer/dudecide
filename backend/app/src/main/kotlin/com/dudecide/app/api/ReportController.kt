package com.dudecide.app.api

import com.dudecide.app.api.dto.MeetingReportResponse
import com.dudecide.app.domain.ReportService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/reports")
@Tag(name = "Reports")
class ReportController(
    private val reportService: ReportService,
) {

    @GetMapping("/latest")
    @Operation(summary = "Get the latest materialized meeting report")
    fun getLatest(@PathVariable meetingId: UUID): ResponseEntity<MeetingReportResponse> =
        ResponseEntity.ok(reportService.getLatestReport(meetingId))

    @PostMapping("/refresh")
    @Operation(summary = "Materialize a fresh meeting report snapshot")
    fun refresh(@PathVariable meetingId: UUID): ResponseEntity<MeetingReportResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(reportService.refreshReport(meetingId))
}
