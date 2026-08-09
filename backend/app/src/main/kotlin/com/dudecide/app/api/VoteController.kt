package com.dudecide.app.api

import com.dudecide.app.domain.VoteService
import com.dudecide.app.api.dto.VoteResponseView
import com.dudecide.app.api.dto.VoteSubmissionRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Votes")
class VoteController(
    private val voteService: VoteService,
) {

    @GetMapping("/votes/{voteId}")
    @Operation(summary = "Get vote detail and aggregated results")
    fun getVote(@PathVariable voteId: UUID): ResponseEntity<VoteResponseView> =
        ResponseEntity.ok(voteService.getVote(voteId))

    @PutMapping("/votes/{voteId}/responses/me")
    @Operation(summary = "Submit current user vote responses")
    fun submitResponses(
        @PathVariable voteId: UUID,
        @RequestBody request: VoteSubmissionRequest,
    ): ResponseEntity<VoteResponseView> =
        ResponseEntity.ok(voteService.submitResponses(voteId, request))
}
