package com.dudecide.app.api.dto

import java.time.OffsetDateTime
import java.util.UUID

data class ThreadSummaryView(
    val id: UUID,
    val category: String,
    val title: String,
    val status: String,
    val sortOrder: Int,
    val lastActivityAt: OffsetDateTime? = null,
    val messageCount: Int,
    val decisionCount: Int,
    val openVoteCount: Int,
)

data class CreateMessageRequest(
    val body: String,
    val attachments: List<Map<String, Any?>> = emptyList(),
)

data class MessageView(
    val id: UUID,
    val threadId: UUID,
    val senderUserId: UUID,
    val body: String,
    val attachments: List<Map<String, Any?>> = emptyList(),
    val aiLabels: Map<String, Any?> = emptyMap(),
    val createdAt: OffsetDateTime,
    val editedAt: OffsetDateTime? = null,
)
