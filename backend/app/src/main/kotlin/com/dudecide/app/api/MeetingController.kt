package com.dudecide.app.api

import com.dudecide.app.api.dto.CreateMeetingRequest
import com.dudecide.app.api.dto.CreateInviteLinkRequest
import com.dudecide.app.api.dto.MeetingDetailResponse
import com.dudecide.app.api.dto.MeetingResponse
import com.dudecide.app.api.dto.InviteLinkResponse
import com.dudecide.app.api.dto.MembershipResponse
import com.dudecide.app.api.dto.UpdateMeetingRequest
import com.dudecide.app.api.dto.UpdateMembershipRequest
import com.dudecide.app.domain.IdempotencyService
import com.dudecide.app.domain.IdempotentResponse
import com.dudecide.app.domain.MeetingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/meetings")
@Tag(name = "Meetings")
class MeetingController(
    private val meetingService: MeetingService,
    private val idempotencyService: IdempotencyService,
) {

    @GetMapping("/{meetingId}")
    @Operation(summary = "Get meeting summary and progress")
    fun getMeeting(@PathVariable meetingId: UUID): ResponseEntity<MeetingDetailResponse> =
        ResponseEntity.ok(meetingService.getMeeting(meetingId))

    @PostMapping("/{meetingId}/invite-links")
    @Operation(summary = "Create an invite link")
    fun createInviteLink(
        @PathVariable meetingId: UUID,
        @RequestBody request: CreateInviteLinkRequest,
    ): ResponseEntity<InviteLinkResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(meetingService.createInviteLink(meetingId, request))

    @PostMapping
    @Operation(summary = "Create a meeting")
    fun createMeeting(
        @RequestBody request: CreateMeetingRequest,
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<MeetingResponse> {
        val response = idempotencyService.execute(
            operationScope = "meetings.create",
            idempotencyKey = idempotencyKey,
            responseType = MeetingResponse::class.java,
        ) {
            IdempotentResponse(HttpStatus.CREATED.value(), meetingService.createMeeting(request))
        }
        return ResponseEntity.status(response.status).body(response.body)
    }

    @PatchMapping("/{meetingId}")
    @Operation(summary = "Update meeting metadata")
    fun updateMeeting(
        @PathVariable meetingId: UUID,
        @RequestBody request: UpdateMeetingRequest,
    ): ResponseEntity<MeetingDetailResponse> =
        ResponseEntity.ok(meetingService.updateMeeting(meetingId, request))

    @PatchMapping("/{meetingId}/members/{memberId}")
    @Operation(summary = "Update meeting member role or attendance")
    fun updateMembership(
        @PathVariable meetingId: UUID,
        @PathVariable memberId: UUID,
        @RequestBody request: UpdateMembershipRequest,
    ): ResponseEntity<MembershipResponse> =
        ResponseEntity.ok(meetingService.updateMembership(meetingId, memberId, request))
}
