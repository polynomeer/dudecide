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

data class UpdateMeetingRequest(
    val title: String? = null,
    val meetingType: String? = null,
    val status: String? = null,
    val originRegion: String? = null,
    val expectedHeadcount: Int? = null,
    val budget: BudgetRangeDto? = null,
    val startsAt: OffsetDateTime? = null,
    val endsAt: OffsetDateTime? = null,
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

data class MeetingDetailResponse(
    val id: UUID,
    val title: String,
    val meetingType: String,
    val status: MeetingStatusDto,
    val originRegion: String? = null,
    val expectedHeadcount: Int? = null,
    val budget: BudgetRangeDto? = null,
    val ownerUserId: UUID,
    val memberCount: Int,
    val decisionCount: Int,
    val openVoteCount: Int,
    val threads: List<ThreadSummaryView> = emptyList(),
)

data class CreateInviteLinkRequest(
    val expiresAt: OffsetDateTime? = null,
)

data class InviteLinkResponse(
    val id: UUID,
    val meetingId: UUID,
    val token: String,
    val status: String,
    val expiresAt: OffsetDateTime? = null,
    val createdAt: OffsetDateTime,
)

data class JoinInviteRequest(
    val displayName: String,
)

data class MembershipResponse(
    val id: UUID,
    val meetingId: UUID,
    val userId: UUID,
    val displayName: String,
    val role: String,
    val attendanceStatus: String,
    val joinedAt: OffsetDateTime,
)

data class UpdateMembershipRequest(
    val role: String? = null,
    val attendanceStatus: String? = null,
)

data class MeetingReportResponse(
    val id: UUID,
    val meetingId: UUID,
    val version: Int,
    val snapshot: Map<String, Any?> = emptyMap(),
    val generatedAt: OffsetDateTime,
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

data class CreateDecisionRequest(
    val threadId: UUID,
    val decisionType: String,
    val title: String,
    val proposedResult: Map<String, Any?> = emptyMap(),
)

data class ReconsiderDecisionRequest(
    val reason: String,
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

data class DecisionDetailResponse(
    val id: UUID,
    val meetingId: UUID,
    val threadId: UUID,
    val decisionType: String,
    val title: String,
    val status: DecisionStatus,
    val proposedResult: Map<String, Any?> = emptyMap(),
    val confirmedResult: Map<String, Any?> = emptyMap(),
    val decidedAt: OffsetDateTime? = null,
    val evidence: List<DecisionEvidenceView> = emptyList(),
    val history: List<DecisionHistoryView> = emptyList(),
)

data class DecisionEvidenceView(
    val id: UUID,
    val messageId: UUID,
    val threadId: UUID,
    val evidenceType: String,
    val quoteSnapshot: String,
    val confidence: Double? = null,
    val createdAt: OffsetDateTime? = null,
)

data class DecisionHistoryView(
    val id: UUID,
    val actorUserId: UUID? = null,
    val action: String,
    val reason: String? = null,
    val beforeState: Map<String, Any?> = emptyMap(),
    val afterState: Map<String, Any?> = emptyMap(),
    val createdAt: OffsetDateTime? = null,
)
