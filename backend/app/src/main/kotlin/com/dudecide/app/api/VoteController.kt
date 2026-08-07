package com.dudecide.app.api

import com.dudecide.app.api.dto.VoteResponseView
import com.dudecide.app.api.dto.VoteSubmissionRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Votes")
class VoteController {

    @PutMapping("/votes/{voteId}/responses/me")
    @Operation(summary = "Submit current user vote responses")
    fun submitResponses(
        @PathVariable voteId: UUID,
        @RequestBody request: VoteSubmissionRequest,
    ): ResponseEntity<VoteResponseView> {
        @Suppress("UNUSED_VARIABLE")
        val ignored = voteId to request
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
    }
}
