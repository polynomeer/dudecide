package com.dudecide.app.domain

import com.dudecide.app.api.dto.UpdateMessageRequest
import com.dudecide.app.api.dto.CreateMessageRequest
import com.dudecide.app.api.dto.MessageSpeakerView
import com.dudecide.app.api.dto.MessageView
import com.dudecide.app.jooq.enums.MessageOrigin
import com.dudecide.app.jooq.tables.AppUser.Companion.APP_USER
import com.dudecide.app.jooq.tables.ChatImport.Companion.CHAT_IMPORT
import com.dudecide.app.jooq.tables.ImportSpeaker.Companion.IMPORT_SPEAKER
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.Message.Companion.MESSAGE
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.DSLContext
import org.jooq.JSON
import org.jooq.Record
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
    fun listMessages(threadId: UUID, before: OffsetDateTime? = null, limit: Int = 50): List<MessageView> {
        meetingAccessService.requireThreadMember(threadId)
        requireThread(threadId)
        if (limit !in 1..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 100")
        }

        val actorId = localActorProvider.currentUserId()
        val query = dsl.select(
            MESSAGE.ID,
            MESSAGE.THREAD_ID,
            MESSAGE.SENDER_USER_ID,
            MESSAGE.ORIGIN,
            MESSAGE.BODY,
            MESSAGE.ATTACHMENTS,
            MESSAGE.AI_LABELS,
            MESSAGE.CREATED_AT,
            MESSAGE.EDITED_AT,
            SENDER_USER.DISPLAY_NAME,
            IMPORT_SPEAKER.ID,
            IMPORT_SPEAKER.DISPLAY_NAME,
            IMPORT_SPEAKER.PSEUDONYM,
            MAPPED_USER.ID,
            MAPPED_USER.DISPLAY_NAME,
            CHAT_IMPORT.UPLOADED_BY_USER_ID,
        )
            .from(MESSAGE)
            .leftJoin(SENDER_USER).on(SENDER_USER.ID.eq(MESSAGE.SENDER_USER_ID))
            .leftJoin(IMPORT_SPEAKER).on(IMPORT_SPEAKER.ID.eq(MESSAGE.IMPORT_SPEAKER_ID))
            .leftJoin(MAPPED_MEMBERSHIP).on(MAPPED_MEMBERSHIP.ID.eq(IMPORT_SPEAKER.MEMBERSHIP_ID))
            .leftJoin(MAPPED_USER).on(MAPPED_USER.ID.eq(MAPPED_MEMBERSHIP.USER_ID))
            .leftJoin(CHAT_IMPORT).on(CHAT_IMPORT.ID.eq(MESSAGE.IMPORT_ID))
            .where(MESSAGE.THREAD_ID.eq(threadId))

        if (before != null) {
            query.and(MESSAGE.CREATED_AT.lt(before))
        }

        return query
            .orderBy(MESSAGE.CREATED_AT.desc())
            .limit(limit)
            .fetch()
            .reversed()
            .map { record ->
                val origin = requireNotNull(record.get(MESSAGE.ORIGIN))
                MessageView(
                    id = requireNotNull(record.get(MESSAGE.ID)),
                    threadId = requireNotNull(record.get(MESSAGE.THREAD_ID)),
                    senderUserId = record.get(MESSAGE.SENDER_USER_ID),
                    origin = origin.literal,
                    speaker = resolveSpeaker(record, origin, actorId),
                    body = requireNotNull(record.get(MESSAGE.BODY)),
                    attachments = jsonToList(record.get(MESSAGE.ATTACHMENTS)),
                    aiLabels = jsonToMap(record.get(MESSAGE.AI_LABELS)),
                    createdAt = requireNotNull(record.get(MESSAGE.CREATED_AT)),
                    editedAt = record.get(MESSAGE.EDITED_AT),
                )
            }
    }

    @Transactional
    fun postMessage(threadId: UUID, request: CreateMessageRequest): MessageView {
        meetingAccessService.requireThreadMember(threadId)
        val senderUserId = localActorProvider.currentUserId()
        val thread = requireThread(threadId)
        if (thread.readOnly == true) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "thread is read-only")
        }

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
            speaker = nativeSpeaker(senderUserId),
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
            senderUserId = record.senderUserId,
            speaker = nativeSpeaker(requireNotNull(record.senderUserId)),
            body = request.body,
            attachments = request.attachments,
            aiLabels = jsonToMap(record.aiLabels),
            createdAt = requireNotNull(record.createdAt),
            editedAt = editedAt,
        )
    }

    private fun nativeSpeaker(userId: UUID): MessageSpeakerView {
        val displayName = dsl.select(APP_USER.DISPLAY_NAME)
            .from(APP_USER)
            .where(APP_USER.ID.eq(userId))
            .fetchOne(APP_USER.DISPLAY_NAME)
        return MessageSpeakerView(displayName = displayName ?: UNKNOWN_SPEAKER, userId = userId)
    }

    // Imported speakers show a member's name once mapped; otherwise only the uploader sees the exported name.
    private fun resolveSpeaker(record: Record, origin: MessageOrigin, actorId: UUID): MessageSpeakerView {
        val importSpeakerId = record.get(IMPORT_SPEAKER.ID)
        if (origin != MessageOrigin.imported || importSpeakerId == null) {
            return MessageSpeakerView(
                displayName = record.get(SENDER_USER.DISPLAY_NAME) ?: UNKNOWN_SPEAKER,
                userId = record.get(MESSAGE.SENDER_USER_ID),
            )
        }

        val mappedUserId = record.get(MAPPED_USER.ID)
        if (mappedUserId != null) {
            return MessageSpeakerView(
                displayName = record.get(MAPPED_USER.DISPLAY_NAME) ?: UNKNOWN_SPEAKER,
                userId = mappedUserId,
                importSpeakerId = importSpeakerId,
            )
        }

        if (record.get(CHAT_IMPORT.UPLOADED_BY_USER_ID) == actorId) {
            return MessageSpeakerView(
                displayName = requireNotNull(record.get(IMPORT_SPEAKER.DISPLAY_NAME)),
                importSpeakerId = importSpeakerId,
            )
        }

        return MessageSpeakerView(
            displayName = requireNotNull(record.get(IMPORT_SPEAKER.PSEUDONYM)),
            importSpeakerId = importSpeakerId,
            pseudonymized = true,
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

    private companion object {
        const val UNKNOWN_SPEAKER = "Unknown"

        val SENDER_USER = APP_USER.`as`("sender_user")
        val MAPPED_MEMBERSHIP = MEMBERSHIP.`as`("mapped_membership")
        val MAPPED_USER = APP_USER.`as`("mapped_user")
    }
}
