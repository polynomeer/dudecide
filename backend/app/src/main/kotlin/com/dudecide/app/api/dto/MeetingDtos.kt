package com.dudecide.app.api.dto

import com.dudecide.shared.decision.DecisionStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Create a new meeting room")
data class CreateMeetingRequest(
    @field:Schema(example = "2026 Summer Rafting")
    val title: String,
    @field:Schema(example = "trip")
    val meetingType: String,
    val expectedHeadcount: Int? = null,
    val originRegion: String? = null,
    val budget: BudgetRangeDto? = null,
)

data class BudgetRangeDto(
    val min: Double? = null,
    val max: Double? = null,
)

data class MeetingResponse(
    val id: UUID,
    val title: String,
    val meetingType: String,
    val status: MeetingStatusDto,
)

enum class MeetingStatusDto {
    DRAFT,
    ACTIVE,
    FINALIZING,
    COMPLETED,
    ARCHIVED,
}

data class ConfirmDecisionRequest(
    val confirmedResult: Map<String, Any?>,
    val reason: String? = null,
)

data class DecisionResponse(
    val id: UUID,
    val meetingId: UUID,
    val threadId: UUID,
    val decisionType: String,
    val title: String,
    val status: DecisionStatus,
    val proposedResult: Map<String, Any?> = emptyMap(),
    val confirmedResult: Map<String, Any?> = emptyMap(),
    val decidedAt: OffsetDateTime? = null,
)
