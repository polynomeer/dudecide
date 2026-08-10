package com.dudecide.app.api

import com.dudecide.app.api.dto.JoinInviteRequest
import com.dudecide.app.api.dto.MembershipResponse
import com.dudecide.app.domain.MeetingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/invite-links")
@Tag(name = "Invites")
class InviteController(
    private val meetingService: MeetingService,
) {

    @PostMapping("/{token}/join")
    @Operation(summary = "Join a meeting via invite token")
    fun joinWithInvite(
        @PathVariable token: String,
        @RequestBody request: JoinInviteRequest,
    ): ResponseEntity<MembershipResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(meetingService.joinWithInvite(token, request))
}
