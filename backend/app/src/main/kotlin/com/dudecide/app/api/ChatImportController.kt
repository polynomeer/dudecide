package com.dudecide.app.api

import com.dudecide.app.api.dto.ApplyChatImportRequest
import com.dudecide.app.api.dto.ApplyChatImportResponse
import com.dudecide.app.api.dto.ChatImportCreatedResponse
import com.dudecide.app.api.dto.ChatImportView
import com.dudecide.app.api.dto.ClaimImportSpeakerRequest
import com.dudecide.app.api.dto.ClaimImportSpeakerResponse
import com.dudecide.app.api.dto.CreateChatImportRequest
import com.dudecide.app.api.dto.DeleteChatImportResponse
import com.dudecide.app.api.dto.ImportSpeakerView
import com.dudecide.app.api.dto.MessageView
import com.dudecide.app.api.dto.UpdateImportSpeakerRequest
import com.dudecide.app.domain.IdempotencyService
import com.dudecide.app.domain.IdempotentResponse
import com.dudecide.app.domain.chatimport.ChatImportApplyService
import com.dudecide.app.domain.chatimport.ChatImportService
import com.dudecide.app.domain.chatimport.ImportSpeakerService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Chat Imports")
class ChatImportController(
    private val chatImportService: ChatImportService,
    private val chatImportApplyService: ChatImportApplyService,
    private val importSpeakerService: ImportSpeakerService,
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
    fun getImport(
        @PathVariable importId: UUID,
        @RequestParam(required = false, defaultValue = "false") includeDeletionImpact: Boolean,
    ): ResponseEntity<ChatImportView> =
        ResponseEntity.ok(chatImportService.getImport(importId, includeDeletionImpact))

    @GetMapping("/imports/{importId}/evidence")
    @Operation(summary = "Get the messages cited by a staged import result")
    fun getEvidence(@PathVariable importId: UUID): ResponseEntity<List<MessageView>> =
        ResponseEntity.ok(chatImportService.getEvidence(importId))

    @PostMapping("/imports/{importId}/apply")
    @Operation(summary = "Apply selected staged items to the meeting board")
    fun applyImport(
        @PathVariable importId: UUID,
        @RequestBody request: ApplyChatImportRequest,
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<ApplyChatImportResponse> {
        val response = idempotencyService.execute(
            operationScope = "imports.$importId.apply",
            idempotencyKey = idempotencyKey,
            responseType = ApplyChatImportResponse::class.java,
        ) {
            IdempotentResponse(HttpStatus.OK.value(), chatImportApplyService.apply(importId, request))
        }
        return ResponseEntity.status(response.status).body(response.body)
    }

    @DeleteMapping("/imports/{importId}")
    @Operation(summary = "Delete an import, its messages, and evidence built on them")
    fun deleteImport(@PathVariable importId: UUID): ResponseEntity<DeleteChatImportResponse> =
        ResponseEntity.ok(chatImportService.deleteImport(importId))

    @GetMapping("/meetings/{meetingId}/import-speakers")
    @Operation(summary = "List imported speakers and their member mapping")
    fun listSpeakers(@PathVariable meetingId: UUID): ResponseEntity<List<ImportSpeakerView>> =
        ResponseEntity.ok(importSpeakerService.listSpeakers(meetingId))

    @PatchMapping("/import-speakers/{speakerId}")
    @Operation(summary = "Map or unmap an imported speaker to a member")
    fun updateSpeaker(
        @PathVariable speakerId: UUID,
        @RequestBody request: UpdateImportSpeakerRequest,
    ): ResponseEntity<ImportSpeakerView> =
        ResponseEntity.ok(importSpeakerService.updateSpeaker(speakerId, request))

    @PostMapping("/meetings/{meetingId}/import-speakers/claim")
    @Operation(summary = "Claim the imported speaker that is the current member")
    fun claimSpeaker(
        @PathVariable meetingId: UUID,
        @RequestBody request: ClaimImportSpeakerRequest,
    ): ResponseEntity<ClaimImportSpeakerResponse> =
        ResponseEntity.ok(importSpeakerService.claimSpeaker(meetingId, request))

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
