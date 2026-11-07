package com.dudecide.app.api

import com.dudecide.app.api.dto.MarkNotificationsReadResponse
import com.dudecide.app.api.dto.NotificationView
import com.dudecide.app.domain.NotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/notifications")
@Tag(name = "Notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {

    @GetMapping
    @Operation(summary = "List notifications for the current meeting user")
    fun listNotifications(@PathVariable meetingId: UUID): ResponseEntity<List<NotificationView>> =
        ResponseEntity.ok(notificationService.listNotifications(meetingId))

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Mark one notification as read")
    fun markRead(
        @PathVariable meetingId: UUID,
        @PathVariable notificationId: UUID,
    ): ResponseEntity<NotificationView> =
        ResponseEntity.ok(notificationService.markRead(meetingId, notificationId))

    @PostMapping("/read-all")
    @Operation(summary = "Mark every unread notification of the current user as read")
    fun markAllRead(@PathVariable meetingId: UUID): ResponseEntity<MarkNotificationsReadResponse> =
        ResponseEntity.ok(notificationService.markAllRead(meetingId))
}
