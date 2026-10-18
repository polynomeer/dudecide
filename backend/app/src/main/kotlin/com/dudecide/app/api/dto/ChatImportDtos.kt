package com.dudecide.app.api.dto

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// Optional request fields are nullable: without jackson-module-kotlin, Kotlin defaults are not applied on missing JSON fields.
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
)
