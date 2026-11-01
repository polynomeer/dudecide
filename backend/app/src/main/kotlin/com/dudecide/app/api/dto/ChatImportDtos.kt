package com.dudecide.app.api.dto

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class CreateChatImportRequest(
    val source: ChatImportSourceInput,
    val range: ChatImportRangeInput,
    val speakers: List<ChatImportSpeakerInput>,
    val messages: List<ChatImportMessageInput>,
    val consent: ChatImportConsentInput,
    val mediaCounts: List<ChatImportMediaCountInput>? = null,
    val stats: ChatImportStatsInput? = null,
    val meetingTitle: String? = null,
    val meetingType: String? = null,
)

data class ChatImportSourceInput(
    val type: String,
    val format: String,
    val formatVersion: String? = null,
    val roomName: String? = null,
)

data class ChatImportRangeInput(
    val start: OffsetDateTime,
    val end: OffsetDateTime,
)

data class ChatImportSpeakerInput(
    val key: String,
    val displayName: String,
    val isSelf: Boolean? = null,
    val membershipId: UUID? = null,
)

data class ChatImportMessageInput(
    val speakerKey: String,
    val sentAt: OffsetDateTime,
    val body: String,
)

data class ChatImportMediaCountInput(
    val date: LocalDate,
    val count: Int,
)

data class ChatImportStatsInput(
    val parsedCount: Int? = null,
    val uploadedCount: Int? = null,
    val maskedCount: Int? = null,
    val unparsedLineCount: Int? = null,
)

data class ChatImportConsentInput(
    val isParticipant: Boolean,
    val version: String,
)

data class ChatImportCreatedResponse(
    val importId: UUID,
    val meetingId: UUID,
    val threadId: UUID,
    val status: String,
    val messageCount: Int,
    val duplicateCount: Int,
)

data class ChatImportView(
    val id: UUID,
    val meetingId: UUID,
    val threadId: UUID,
    val uploadedByUserId: UUID,
    val sourceType: String,
    val sourceFormat: String,
    val sourceRoomName: String? = null,
    val rangeStart: OffsetDateTime,
    val rangeEnd: OffsetDateTime,
    val messageCount: Int,
    val duplicateCount: Int,
    val status: String,
    val progress: Map<String, Any?> = emptyMap(),
    val result: Map<String, Any?>? = null,
    val failureReason: String? = null,
    val purgeAt: OffsetDateTime? = null,
    val createdAt: OffsetDateTime,
    val completedAt: OffsetDateTime? = null,
    val deletionImpact: ChatImportDeletionImpact? = null,
)

data class ApplyChatImportRequest(
    val itemIds: List<String>,
)

data class ApplyChatImportResponse(
    val importId: UUID,
    val appliedItemIds: List<String>,
    val skippedItemIds: List<String>,
    val decisionIds: List<UUID>,
    val taskIds: List<UUID>,
    val meetingStatus: String,
)

data class ChatImportDeletionImpact(
    val removedMessages: Int,
    val removedEvidence: Int,
    val affectedConfirmedDecisions: Int,
)

data class DeleteChatImportResponse(
    val importId: UUID,
    val removedMessages: Int,
    val removedEvidence: Int,
    val affectedConfirmedDecisions: Int,
)

data class ImportSpeakerView(
    val id: UUID,
    val threadId: UUID,
    val label: String,
    val mapped: Boolean,
    val membershipId: UUID? = null,
    val displayName: String? = null,
    val pseudonym: String? = null,
    /** True when this speaker is mapped to the caller's own membership. */
    val mine: Boolean = false,
)

data class UpdateImportSpeakerRequest(
    val membershipId: UUID? = null,
)

data class ClaimImportSpeakerRequest(
    val displayName: String,
)

data class ClaimImportSpeakerResponse(
    val claimedSpeakerIds: List<UUID>,
)

data class InvitePreviewResponse(
    val meetingTitle: String,
    val meetingStatus: String,
    val memberCount: Int,
    val decisions: List<InvitePreviewDecision>,
    val pendingDecisionCount: Int,
    val confirmedDecisionCount: Int,
    val openTaskCount: Int,
    val openQuestionCount: Int,
    val meetingType: String? = null,
    val startsAt: OffsetDateTime? = null,
    val endsAt: OffsetDateTime? = null,
    val tasks: List<InvitePreviewTask> = emptyList(),
)

data class InvitePreviewDecision(
    val title: String,
    val decisionType: String,
    val status: String,
    val decidedAt: OffsetDateTime? = null,
)

/** A task as shown before joining: no owner name, only whether someone has taken it. */
data class InvitePreviewTask(
    val title: String,
    val status: String,
    val hasOwner: Boolean,
    val dueAt: OffsetDateTime? = null,
)

data class ParseTelemetryRequest(
    val format: String,
    val formatVersion: String? = null,
    val parsedCount: Int = 0,
    val unparsedLineCount: Int = 0,
    /** Line shapes such as `DDDDH DDH DH H D:DD, H : H`, never content. */
    val signatures: List<String> = emptyList(),
)
