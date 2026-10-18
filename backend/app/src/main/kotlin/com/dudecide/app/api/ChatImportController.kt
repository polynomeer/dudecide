package com.dudecide.app.api

import com.dudecide.app.api.dto.ChatImportCreatedResponse
import com.dudecide.app.api.dto.ChatImportView
import com.dudecide.app.api.dto.CreateChatImportRequest
import com.dudecide.app.domain.IdempotencyService
import com.dudecide.app.domain.IdempotentResponse
import com.dudecide.app.domain.chatimport.ChatImportService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Chat Imports")
class ChatImportController(
    private val chatImportService: ChatImportService,
    private val idempotencyService: IdempotencyService,
) {

    @PostMapping("/imports")
    @Operation(summary = "Import a chat into a new draft meeting")
    fun createImport(
        @RequestBody request: CreateChatImportRequest,
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<ChatImportCreatedResponse> {
        requireBodyWithinLimit(servletRequest)
        val response = idempotencyService.execute(
            operationScope = "imports.create",
            idempotencyKey = idempotencyKey,
            responseType = ChatImportCreatedResponse::class.java,
        ) {
            IdempotentResponse(HttpStatus.ACCEPTED.value(), chatImportService.createImport(request))
        }
        return ResponseEntity.status(response.status).body(response.body)
    }

    @PostMapping("/meetings/{meetingId}/imports")
    @Operation(summary = "Import a chat into an existing meeting")
    fun createMeetingImport(
        @PathVariable meetingId: UUID,
        @RequestBody request: CreateChatImportRequest,
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<ChatImportCreatedResponse> {
        requireBodyWithinLimit(servletRequest)
        val response = idempotencyService.execute(
            operationScope = "meetings.$meetingId.imports.create",
            idempotencyKey = idempotencyKey,
            responseType = ChatImportCreatedResponse::class.java,
        ) {
            IdempotentResponse(HttpStatus.ACCEPTED.value(), chatImportService.createMeetingImport(meetingId, request))
        }
        return ResponseEntity.status(response.status).body(response.body)
    }

    @GetMapping("/meetings/{meetingId}/imports")
    @Operation(summary = "List chat imports of a meeting")
    fun listImports(@PathVariable meetingId: UUID): ResponseEntity<List<ChatImportView>> =
        ResponseEntity.ok(chatImportService.listImports(meetingId))

    @GetMapping("/imports/{importId}")
    @Operation(summary = "Get chat import status and result")
    fun getImport(@PathVariable importId: UUID): ResponseEntity<ChatImportView> =
        ResponseEntity.ok(chatImportService.getImport(importId))

    // Checked after binding because the body is already buffered; a request filter is the follow-up if abuse appears.
    private fun requireBodyWithinLimit(servletRequest: HttpServletRequest) {
        if (servletRequest.contentLengthLong > MAX_BODY_BYTES) {
            throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "request body exceeds limit of 2 MB")
        }
    }

    private companion object {
        const val MAX_BODY_BYTES = 2L * 1024 * 1024
    }
}
