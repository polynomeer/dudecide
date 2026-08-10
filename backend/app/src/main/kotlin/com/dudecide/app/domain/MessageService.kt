package com.dudecide.app.domain

import com.dudecide.app.api.dto.UpdateMessageRequest
import com.dudecide.app.api.dto.CreateMessageRequest
import com.dudecide.app.api.dto.MessageView
import com.dudecide.app.jooq.tables.TopicThread
import com.dudecide.app.jooq.tables.Message.Companion.MESSAGE
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.DSLContext
import org.jooq.JSON
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.UUID

@Service
class MessageService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    private val localActorProvider: LocalActorProvider,
    private val meetingAccessService: MeetingAccessService,
    private val outboxService: OutboxService,
    private val eventStreamService: EventStreamService,
) {

    @Transactional(readOnly = true)
    fun listMessages(threadId: UUID): List<MessageView> {
        meetingAccessService.requireThreadMember(threadId)
        requireThread(threadId)

        return dsl.selectFrom(MESSAGE)
            .where(MESSAGE.THREAD_ID.eq(threadId))
            .orderBy(MESSAGE.CREATED_AT.asc())
            .fetch()
            .map { record ->
                MessageView(
                    id = requireNotNull(record.id),
                    threadId = requireNotNull(record.threadId),
                    senderUserId = requireNotNull(record.senderUserId),
                    body = requireNotNull(record.body),
                    attachments = jsonToList(record.attachments),
                    aiLabels = jsonToMap(record.aiLabels),
                    createdAt = requireNotNull(record.createdAt),
                    editedAt = record.editedAt,
                )
            }
    }

    @Transactional
    fun postMessage(threadId: UUID, request: CreateMessageRequest): MessageView {
        meetingAccessService.requireThreadMember(threadId)
        val senderUserId = localActorProvider.currentUserId()
        val thread = requireThread(threadId)

        val messageId = UUID.randomUUID()
        val now = OffsetDateTime.now()
        val attachments = JSON.valueOf(objectMapper.writeValueAsString(request.attachments))
        val aiLabels = JSON.valueOf("{}")

        dsl.insertInto(MESSAGE)
            .set(MESSAGE.ID, messageId)
            .set(MESSAGE.THREAD_ID, threadId)
            .set(MESSAGE.SENDER_USER_ID, senderUserId)
            .set(MESSAGE.BODY, request.body)
            .set(MESSAGE.ATTACHMENTS, attachments)
            .set(MESSAGE.AI_LABELS, aiLabels)
            .set(MESSAGE.CREATED_AT, now)
            .execute()

        dsl.update(TOPIC_THREAD)
            .set(TOPIC_THREAD.LAST_ACTIVITY_AT, now)
            .where(TOPIC_THREAD.ID.eq(threadId))
            .execute()

        outboxService.append(
            aggregateType = "thread",
            aggregateId = threadId,
            eventType = "MessageCreated",
            payload = mapOf(
                "messageId" to messageId,
                "threadId" to threadId,
                "senderUserId" to senderUserId,
                "createdAt" to now.toString(),
            ),
        )
        eventStreamService.publish(
            meetingId = requireNotNull(thread.meetingId),
            eventType = "message.created",
            payload = mapOf(
                "meetingId" to requireNotNull(thread.meetingId),
                "threadId" to threadId,
                "messageId" to messageId,
            ),
        )

        return MessageView(
            id = messageId,
            threadId = threadId,
            senderUserId = senderUserId,
            body = request.body,
            attachments = request.attachments,
            aiLabels = emptyMap(),
            createdAt = now,
        )
    }

    @Transactional
    fun updateMessage(messageId: UUID, request: UpdateMessageRequest): MessageView {
        meetingAccessService.requireMessageEditor(messageId)
        val record = dsl.selectFrom(MESSAGE)
            .where(MESSAGE.ID.eq(messageId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "message not found")
        val thread = requireThread(requireNotNull(record.threadId))
        val editedAt = OffsetDateTime.now()
        val attachments = JSON.valueOf(objectMapper.writeValueAsString(request.attachments))

        dsl.update(MESSAGE)
            .set(MESSAGE.BODY, request.body)
            .set(MESSAGE.ATTACHMENTS, attachments)
            .set(MESSAGE.EDITED_AT, editedAt)
            .where(MESSAGE.ID.eq(messageId))
            .execute()

        outboxService.append(
            aggregateType = "thread",
            aggregateId = requireNotNull(record.threadId),
            eventType = "MessageEdited",
            payload = mapOf(
                "messageId" to messageId,
                "threadId" to requireNotNull(record.threadId),
                "editedAt" to editedAt.toString(),
            ),
        )
        eventStreamService.publish(
            meetingId = requireNotNull(thread.meetingId),
            eventType = "message.updated",
            payload = mapOf(
                "meetingId" to requireNotNull(thread.meetingId),
                "threadId" to requireNotNull(record.threadId),
                "messageId" to messageId,
            ),
        )

        return MessageView(
            id = messageId,
            threadId = requireNotNull(record.threadId),
            senderUserId = requireNotNull(record.senderUserId),
            body = request.body,
            attachments = request.attachments,
            aiLabels = jsonToMap(record.aiLabels),
            createdAt = requireNotNull(record.createdAt),
            editedAt = editedAt,
        )
    }

    private fun requireThread(threadId: UUID): com.dudecide.app.jooq.tables.records.TopicThreadRecord {
        return dsl.selectFrom(TOPIC_THREAD)
            .where(TOPIC_THREAD.ID.eq(threadId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "thread not found")
    }

    private fun jsonToMap(value: JSON?): Map<String, Any?> {
        if (value == null || value.data().isBlank()) {
            return emptyMap()
        }

        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(value.data(), Map::class.java) as Map<String, Any?>
    }

    private fun jsonToList(value: JSON?): List<Map<String, Any?>> {
        if (value == null || value.data().isBlank()) {
            return emptyList()
        }

        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(value.data(), List::class.java) as List<Map<String, Any?>>
    }
}
