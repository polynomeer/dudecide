package com.dudecide.app.api.dto

import java.time.OffsetDateTime
import java.util.UUID

data class CreateThreadRequest(
    val category: String,
    val title: String,
)

data class UpdateMessageRequest(
    val body: String,
    val attachments: List<Map<String, Any?>> = emptyList(),
)

data class AddDecisionEvidenceRequest(
    val messageId: UUID,
    val evidenceType: String,
    val quoteSnapshot: String? = null,
    val confidence: Double? = null,
)

data class SearchResponse(
    val meetingId: UUID,
    val query: String,
    val messages: List<SearchMessageView> = emptyList(),
    val decisions: List<SearchDecisionView> = emptyList(),
    val tasks: List<TaskView> = emptyList(),
)

data class SearchMessageView(
    val id: UUID,
    val threadId: UUID,
    val body: String,
    val createdAt: OffsetDateTime,
)

data class SearchDecisionView(
    val id: UUID,
    val threadId: UUID,
    val title: String,
    val status: String,
)

data class CreateTaskRequest(
    val title: String,
    val ownerUserId: UUID? = null,
    val sourceMessageId: UUID? = null,
    val dueAt: OffsetDateTime? = null,
)

data class UpdateTaskRequest(
    val title: String? = null,
    val status: String? = null,
    val ownerUserId: UUID? = null,
    val dueAt: OffsetDateTime? = null,
)

data class TaskView(
    val id: UUID,
    val meetingId: UUID,
    val ownerUserId: UUID? = null,
    val sourceMessageId: UUID? = null,
    val title: String,
    val status: String,
    val dueAt: OffsetDateTime? = null,
    val createdAt: OffsetDateTime,
)

data class NotificationView(
    val id: UUID,
    val meetingId: UUID,
    val recipientUserId: UUID,
    val type: String,
    val payload: Map<String, Any?> = emptyMap(),
    val sentAt: OffsetDateTime? = null,
    val createdAt: OffsetDateTime,
)
