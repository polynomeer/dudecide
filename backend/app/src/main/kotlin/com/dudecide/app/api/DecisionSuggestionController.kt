package com.dudecide.app.api

import com.dudecide.app.api.dto.DecisionResponse
import com.dudecide.app.api.dto.DecisionSuggestionView
import com.dudecide.app.domain.suggestion.DecisionSuggestionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Decision Suggestions")
class DecisionSuggestionController(
    private val decisionSuggestionService: DecisionSuggestionService,
) {

    @GetMapping("/threads/{threadId}/decision-suggestions")
    @Operation(summary = "List pending AI decision suggestions for a topic thread")
    fun listPending(@PathVariable threadId: UUID): ResponseEntity<List<DecisionSuggestionView>> =
        ResponseEntity.ok(decisionSuggestionService.listPending(threadId))

    @PostMapping("/decision-suggestions/{suggestionId}/accept")
    @Operation(summary = "Turn a suggestion into a decision draft with its evidence (host or co-host)")
    fun accept(@PathVariable suggestionId: UUID): ResponseEntity<DecisionResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(decisionSuggestionService.accept(suggestionId))

    @PostMapping("/decision-suggestions/{suggestionId}/dismiss")
    @Operation(summary = "Dismiss a suggestion so it is not shown again (host or co-host)")
    fun dismiss(@PathVariable suggestionId: UUID): ResponseEntity<DecisionSuggestionView> =
        ResponseEntity.ok(decisionSuggestionService.dismiss(suggestionId))
}
