package com.dudecide.app.api

import com.dudecide.app.domain.EventStreamService
import com.dudecide.app.domain.MeetingAccessService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/events")
@Tag(name = "Realtime")
class RealtimeController(
    private val eventStreamService: EventStreamService,
    private val meetingAccessService: MeetingAccessService,
) {

    @GetMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(summary = "Open a meeting SSE stream")
    fun stream(@PathVariable meetingId: UUID): SseEmitter {
        meetingAccessService.requireMeetingMember(meetingId)
        return eventStreamService.open(meetingId)
    }
}
