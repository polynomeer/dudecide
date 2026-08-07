package com.dudecide.app.api

import com.dudecide.app.api.dto.CreateMeetingRequest
import com.dudecide.app.api.dto.MeetingResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/meetings")
@Tag(name = "Meetings")
class MeetingController {

    @PostMapping
    @Operation(summary = "Create a meeting")
    fun createMeeting(@RequestBody request: CreateMeetingRequest): ResponseEntity<MeetingResponse> {
        @Suppress("UNUSED_VARIABLE")
        val ignored = request
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
    }
}
