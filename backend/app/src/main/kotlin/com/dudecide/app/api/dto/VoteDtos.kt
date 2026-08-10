package com.dudecide.app.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

data class CreateVoteRequest(
    val threadId: UUID,
    val decisionId: UUID? = null,
    val decisionType: String? = null,
    val title: String,
    @field:Schema(allowableValues = ["date_matrix", "package_preference", "binary_confirm"])
    val voteType: String,
    val opensAt: OffsetDateTime? = null,
    val deadlineAt: OffsetDateTime? = null,
    val options: List<CreateVoteOptionRequest>,
)

data class CreateVoteOptionRequest(
    val optionType: String,
    val label: String,
    val payload: Map<String, Any?> = emptyMap(),
    val sortOrder: Int? = null,
)

data class VoteSubmissionRequest(
    val responses: List<VoteSubmissionItem>,
)

data class VoteSubmissionItem(
    val optionId: UUID,
    @field:Schema(allowableValues = ["available", "maybe", "unavailable", "good", "okay", "dislike", "yes", "no"])
    val responseValue: String,
)

data class VoteResponseView(
    val voteId: UUID,
    val meetingId: UUID,
    val decisionId: UUID? = null,
    val threadId: UUID,
    val voteType: String,
    val status: VoteStatusDto,
    val opensAt: OffsetDateTime? = null,
    val deadlineAt: OffsetDateTime? = null,
    val closedAt: OffsetDateTime? = null,
    val options: List<VoteOptionView>,
    val responses: List<VoteSubmissionItem>,
    val aggregates: VoteAggregateView? = null,
)

data class VoteOptionView(
    val id: UUID,
    val optionType: String,
    val label: String,
    val payload: Map<String, Any?> = emptyMap(),
)

data class VoteAggregateView(
    val totalParticipants: Int,
    val respondedParticipants: Int,
    val unresolvedParticipants: Int,
    val canEarlyClose: Boolean? = null,
    val leadingOptionIds: List<UUID> = emptyList(),
    val options: List<VoteAggregateOptionView> = emptyList(),
)

data class VoteAggregateOptionView(
    val optionId: UUID,
    val label: String,
    val counts: Map<String, Int>,
    val rawScore: Double? = null,
    val rank: Int? = null,
)

enum class VoteStatusDto {
    DRAFT,
    OPEN,
    CLOSED,
    SUPERSEDED,
}
