package com.dudecide.app.api

import com.dudecide.app.api.dto.JoinInviteRequest
import com.dudecide.app.api.dto.MembershipResponse
import com.dudecide.app.domain.AuthSessionService
import com.dudecide.app.domain.MeetingService
import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletRequest
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
    private val authSessionService: AuthSessionService,
) {

    @PostMapping("/{token}/join")
    @Operation(summary = "Join a meeting via invite token")
    fun joinWithInvite(
        @PathVariable token: String,
        @RequestBody request: JoinInviteRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<MembershipResponse> {
        val membership = meetingService.joinWithInvite(token, request)
        authSessionService.rememberActor(servletRequest, membership.userId, membership.displayName)
        return ResponseEntity.status(HttpStatus.CREATED).body(membership)
    }
}
