package com.dudecide.app.api.dto

import java.time.OffsetDateTime
import java.util.UUID

data class DecisionSuggestionView(
    val id: UUID,
    val meetingId: UUID,
    val threadId: UUID,
    val status: String,
    /** `likely_decided` or `contested`, from the analysis status rules. */
    val analysisStatus: String,
    val decisionType: String,
    val title: String,
    val proposedResult: Map<String, Any?> = emptyMap(),
    val evidence: List<DecisionSuggestionEvidence> = emptyList(),
    val decisionId: UUID? = null,
    val createdAt: OffsetDateTime,
)

data class DecisionSuggestionEvidence(
    val messageId: UUID,
    val role: String,
)
