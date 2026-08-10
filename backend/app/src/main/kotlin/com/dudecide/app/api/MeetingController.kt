package com.dudecide.app.api

import com.dudecide.app.api.dto.CreateMeetingRequest
import com.dudecide.app.api.dto.MeetingDetailResponse
import com.dudecide.app.api.dto.MeetingResponse
import com.dudecide.app.domain.MeetingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/meetings")
@Tag(name = "Meetings")
class MeetingController(
    private val meetingService: MeetingService,
) {

    @GetMapping("/{meetingId}")
    @Operation(summary = "Get meeting summary and progress")
    fun getMeeting(@PathVariable meetingId: UUID): ResponseEntity<MeetingDetailResponse> =
        ResponseEntity.ok(meetingService.getMeeting(meetingId))

    @PostMapping
    @Operation(summary = "Create a meeting")
    fun createMeeting(@RequestBody request: CreateMeetingRequest): ResponseEntity<MeetingResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(meetingService.createMeeting(request))
    }
}
