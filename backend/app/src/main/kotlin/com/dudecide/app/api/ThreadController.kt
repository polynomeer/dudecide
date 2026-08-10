package com.dudecide.app.api

import com.dudecide.app.api.dto.CreateMessageRequest
import com.dudecide.app.api.dto.MessageView
import com.dudecide.app.api.dto.ThreadSummaryView
import com.dudecide.app.domain.MessageService
import com.dudecide.app.domain.ThreadService
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
@RequestMapping("/api/v1")
class ThreadController(
    private val threadService: ThreadService,
    private val messageService: MessageService,
) {

    @GetMapping("/meetings/{meetingId}/threads")
    @Tag(name = "Threads")
    @Operation(summary = "List threads with badges and activity state")
    fun listThreads(@PathVariable meetingId: UUID): ResponseEntity<List<ThreadSummaryView>> =
        ResponseEntity.ok(threadService.listThreads(meetingId))

    @GetMapping("/threads/{threadId}/messages")
    @Tag(name = "Messages")
    @Operation(summary = "List thread messages")
    fun listMessages(@PathVariable threadId: UUID): ResponseEntity<List<MessageView>> =
        ResponseEntity.ok(messageService.listMessages(threadId))

    @PostMapping("/threads/{threadId}/messages")
    @Tag(name = "Messages")
    @Operation(summary = "Post a message")
    fun postMessage(
        @PathVariable threadId: UUID,
        @RequestBody request: CreateMessageRequest,
    ): ResponseEntity<MessageView> =
        ResponseEntity.status(HttpStatus.CREATED).body(messageService.postMessage(threadId, request))
}
