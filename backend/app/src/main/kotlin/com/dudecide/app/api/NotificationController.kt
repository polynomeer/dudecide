package com.dudecide.app.api

import com.dudecide.app.api.dto.NotificationView
import com.dudecide.app.domain.NotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
}
