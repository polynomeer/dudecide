package com.dudecide.app.api

import com.dudecide.app.api.dto.ConfirmDecisionRequest
import com.dudecide.app.api.dto.CreateDecisionRequest
import com.dudecide.app.api.dto.DecisionDetailResponse
import com.dudecide.app.api.dto.DecisionResponse
import com.dudecide.app.api.dto.ReconsiderDecisionRequest
import com.dudecide.app.domain.DecisionService
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
@Tag(name = "Decisions")
class DecisionController(
    private val decisionService: DecisionService,
) {

    @GetMapping("/decisions/{decisionId}")
    @Operation(summary = "Get decision detail with evidence and history")
    fun getDecision(@PathVariable decisionId: UUID): ResponseEntity<DecisionDetailResponse> =
        ResponseEntity.ok(decisionService.getDecision(decisionId))

    @GetMapping("/meetings/{meetingId}/decisions")
    @Operation(summary = "List meeting decisions")
    fun listDecisions(@PathVariable meetingId: UUID): ResponseEntity<List<DecisionResponse>> =
        ResponseEntity.ok(decisionService.listDecisions(meetingId))

    @PostMapping("/meetings/{meetingId}/decisions")
    @Operation(summary = "Create a manual decision draft")
    fun createDecision(
        @PathVariable meetingId: UUID,
        @RequestBody request: CreateDecisionRequest,
    ): ResponseEntity<DecisionResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(decisionService.createDecision(meetingId, request))

    @PostMapping("/decisions/{decisionId}/confirm")
    @Operation(summary = "Confirm a decision")
    fun confirmDecision(
        @PathVariable decisionId: UUID,
        @RequestBody request: ConfirmDecisionRequest,
    ): ResponseEntity<DecisionResponse> =
        ResponseEntity.ok(decisionService.confirmDecision(decisionId, request))

    @PostMapping("/decisions/{decisionId}/reconsider")
    @Operation(summary = "Move a confirmed decision into reconsideration")
    fun reconsiderDecision(
        @PathVariable decisionId: UUID,
        @RequestBody request: ReconsiderDecisionRequest,
    ): ResponseEntity<DecisionResponse> =
        ResponseEntity.ok(decisionService.reconsiderDecision(decisionId, request))
}
