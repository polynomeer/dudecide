package com.dudecide.app.domain.chatimport

import com.dudecide.app.api.dto.ChatImportCreatedResponse
import com.dudecide.app.api.dto.ChatImportDeletionImpact
import com.dudecide.app.api.dto.ChatImportView
import com.dudecide.app.api.dto.CreateChatImportRequest
import com.dudecide.app.api.dto.CreateMeetingRequest
import com.dudecide.app.api.dto.DeleteChatImportResponse
import com.dudecide.app.api.dto.MessageView
import com.dudecide.app.domain.EventStreamService
import com.dudecide.app.domain.LocalActorProvider
import com.dudecide.app.domain.MeetingAccessService
import com.dudecide.app.domain.MeetingService
import com.dudecide.app.domain.MessageService
import com.dudecide.app.domain.OutboxService
import com.dudecide.app.jooq.enums.ChatImportSource
import com.dudecide.app.jooq.enums.ChatImportStatus
import com.dudecide.app.jooq.enums.DecisionStatus
import com.dudecide.app.jooq.enums.MessageOrigin
import com.dudecide.app.jooq.enums.ThreadCategory
import com.dudecide.app.jooq.tables.ChatImport.Companion.CHAT_IMPORT
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.DecisionEvidence.Companion.DECISION_EVIDENCE
import com.dudecide.app.jooq.tables.ImportSpeaker.Companion.IMPORT_SPEAKER
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.Message.Companion.MESSAGE
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import com.dudecide.app.jooq.tables.records.ChatImportRecord
import com.dudecide.shared.chatimport.ChatContentHasher
import com.dudecide.shared.chatimport.HashableMessage
import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.DSLContext
import org.jooq.JSON
import org.jooq.impl.DSL
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ChatImportService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    private val localActorProvider: LocalActorProvider,
    private val meetingAccessService: MeetingAccessService,
    private val meetingService: MeetingService,
    private val outboxService: OutboxService,
    private val eventStreamService: EventStreamService,
    private val messageService: MessageService,
) {

    private val hasher = ChatContentHasher()

    /** Guest entry point: creates a draft meeting owned by the uploader, purged if never converted. */
    @Transactional
    fun createImport(request: CreateChatImportRequest): ChatImportCreatedResponse {
        val actorId = localActorProvider.currentUserId()
        val validated = ChatImportValidator.validate(request)
        val meeting = meetingService.createMeeting(
            CreateMeetingRequest(
                title = request.meetingTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
                    ?: validated.roomName?.take(200)
                    ?: DEFAULT_MEETING_TITLE,
                meetingType = request.meetingType?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MEETING_TYPE,
            ),
        )
        return persist(meeting.id, actorId, validated, purgeAt = OffsetDateTime.now().plus(GUEST_RETENTION))
    }

    @Transactional
    fun createMeetingImport(meetingId: UUID, request: CreateChatImportRequest): ChatImportCreatedResponse {
        val actorId = meetingAccessService.requireMeetingHostOrCoHost(meetingId)
        val validated = ChatImportValidator.validate(request)
        return persist(meetingId, actorId, validated, purgeAt = null)
    }

    @Transactional(readOnly = true)
    fun getImport(importId: UUID, includeDeletionImpact: Boolean = false): ChatImportView {
        val record = dsl.selectFrom(CHAT_IMPORT)
            .where(CHAT_IMPORT.ID.eq(importId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "import not found")
        meetingAccessService.requireMeetingMember(requireNotNull(record.meetingId))
        val view = toView(record)
        return if (includeDeletionImpact) view.copy(deletionImpact = deletionImpact(importId)) else view
    }

    /** Messages cited by the staged result, with the same speaker visibility as the thread view. */
    @Transactional(readOnly = true)
    fun getEvidence(importId: UUID): List<MessageView> {
        val record = dsl.selectFrom(CHAT_IMPORT)
            .where(CHAT_IMPORT.ID.eq(importId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "import not found")
        meetingAccessService.requireMeetingMember(requireNotNull(record.meetingId))
        @Suppress("UNCHECKED_CAST")
        val items = jsonToMap(record.result)?.get("items") as? List<Map<String, Any?>> ?: emptyList()
        val ids = items.flatMap { item ->
            @Suppress("UNCHECKED_CAST")
            val evidence = item["evidence"] as? List<Map<String, Any?>> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val objections = item["objections"] as? List<String> ?: emptyList()
            evidence.mapNotNull { it["messageId"] as? String } + objections
        }.map(UUID::fromString)
        return messageService.listMessagesByIds(requireNotNull(record.threadId), ids)
    }

    /**
     * Removes the import's messages; evidence built on them cascades away and task sources are cleared.
     * The import row stays as a tombstone (`deleted`, no result) for metrics.
     */
    @Transactional
    fun deleteImport(importId: UUID): DeleteChatImportResponse {
        val record = dsl.selectFrom(CHAT_IMPORT).where(CHAT_IMPORT.ID.eq(importId)).forUpdate().fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "import not found")
        val meetingId = requireNotNull(record.meetingId)
        val actorId = meetingAccessService.requireMeetingMember(meetingId)
        if (actorId != record.uploadedByUserId) {
            meetingAccessService.requireMeetingHostOrCoHost(meetingId)
        }
        if (record.status == ChatImportStatus.deleted) {
            return DeleteChatImportResponse(importId, 0, 0, 0)
        }

        val impact = deletionImpact(importId)
        dsl.deleteFrom(MESSAGE).where(MESSAGE.IMPORT_ID.eq(importId)).execute()
        dsl.update(CHAT_IMPORT)
            .set(CHAT_IMPORT.STATUS, ChatImportStatus.deleted)
            .setNull(CHAT_IMPORT.RESULT)
            .set(CHAT_IMPORT.PROGRESS, JSON.valueOf("{}"))
            .setNull(CHAT_IMPORT.PURGE_AT)
            .where(CHAT_IMPORT.ID.eq(importId))
            .execute()

        outboxService.append(
            aggregateType = "chat_import",
            aggregateId = importId,
            eventType = "ChatImportDeleted",
            payload = mapOf(
                "importId" to importId,
                "meetingId" to meetingId,
                "actorUserId" to actorId,
                "removedMessages" to impact.removedMessages,
                "removedEvidence" to impact.removedEvidence,
            ),
        )
        eventStreamService.publish(
            meetingId = meetingId,
            eventType = "import.deleted",
            payload = mapOf("meetingId" to meetingId, "importId" to importId),
        )

        return DeleteChatImportResponse(
            importId = importId,
            removedMessages = impact.removedMessages,
            removedEvidence = impact.removedEvidence,
            affectedConfirmedDecisions = impact.affectedConfirmedDecisions,
        )
    }

    @Transactional(readOnly = true)
    fun listImports(meetingId: UUID): List<ChatImportView> {
        meetingAccessService.requireMeetingMember(meetingId)
        return dsl.selectFrom(CHAT_IMPORT)
            .where(CHAT_IMPORT.MEETING_ID.eq(meetingId))
            .orderBy(CHAT_IMPORT.CREATED_AT.desc())
            .fetch()
            .map(::toView)
    }

    private fun persist(
        meetingId: UUID,
        actorId: UUID,
        validated: ValidatedChatImport,
        purgeAt: OffsetDateTime?,
    ): ChatImportCreatedResponse {
        val threadId = findOrCreateThread(meetingId, validated.roomName)
        val speakers = upsertSpeakers(meetingId, threadId, actorId, validated.speakers)

        val hashes = hasher.hashAll(
            validated.messages.map {
                HashableMessage(speakers.getValue(it.speakerKey).displayName, it.sentAt.toInstant(), it.body)
            },
        )
        val existingHashes = existingHashes(threadId, hashes)
        val fresh = validated.messages.zip(hashes).filter { (_, hash) -> hash !in existingHashes }
        if (fresh.isEmpty()) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "all messages were already imported")
        }

        val importId = UUID.randomUUID()
        val now = OffsetDateTime.now()
        val duplicateCount = validated.messages.size - fresh.size

        dsl.insertInto(CHAT_IMPORT)
            .set(CHAT_IMPORT.ID, importId)
            .set(CHAT_IMPORT.MEETING_ID, meetingId)
            .set(CHAT_IMPORT.THREAD_ID, threadId)
            .set(CHAT_IMPORT.UPLOADED_BY_USER_ID, actorId)
            .set(CHAT_IMPORT.SOURCE_TYPE, validated.sourceType)
            .set(CHAT_IMPORT.SOURCE_FORMAT, validated.format)
            .set(CHAT_IMPORT.SOURCE_ROOM_NAME, validated.roomName)
            .set(CHAT_IMPORT.RANGE_START, validated.rangeStart)
            .set(CHAT_IMPORT.RANGE_END, validated.rangeEnd)
            .set(CHAT_IMPORT.MESSAGE_COUNT, fresh.size)
            .set(CHAT_IMPORT.DUPLICATE_COUNT, duplicateCount)
            .set(CHAT_IMPORT.STATUS, ChatImportStatus.submitted)
            .set(CHAT_IMPORT.STATS, toJson(validated.stats))
            .set(CHAT_IMPORT.CONSENT_VERSION, validated.consentVersion)
            .set(CHAT_IMPORT.PURGE_AT, purgeAt)
            .set(CHAT_IMPORT.CREATED_AT, now)
            .execute()

        val messageRecords = fresh.map { (message, hash) ->
            dsl.newRecord(MESSAGE).apply {
                id = UUID.randomUUID()
                this.threadId = threadId
                body = message.body
                origin = MessageOrigin.imported
                this.importId = importId
                importSpeakerId = speakers.getValue(message.speakerKey).id
                contentHash = hash
                createdAt = message.sentAt
            }
        }
        dsl.batchInsert(messageRecords).execute()

        dsl.update(TOPIC_THREAD)
            .set(TOPIC_THREAD.LAST_ACTIVITY_AT, now)
            .where(TOPIC_THREAD.ID.eq(threadId))
            .execute()

        outboxService.append(
            aggregateType = "chat_import",
            aggregateId = importId,
            eventType = "ChatImportSubmitted",
            payload = mapOf(
                "importId" to importId,
                "meetingId" to meetingId,
                "threadId" to threadId,
                "messageCount" to fresh.size,
            ),
        )
        eventStreamService.publish(
            meetingId = meetingId,
            eventType = "import.progress",
            payload = mapOf(
                "meetingId" to meetingId,
                "importId" to importId,
                "status" to ChatImportStatus.submitted.literal,
            ),
        )

        return ChatImportCreatedResponse(
            importId = importId,
            meetingId = meetingId,
            threadId = threadId,
            status = ChatImportStatus.submitted.literal,
            messageCount = fresh.size,
            duplicateCount = duplicateCount,
        )
    }

    // One read-only import thread per (meeting, source room); re-imports reuse it.
    private fun findOrCreateThread(meetingId: UUID, roomName: String?): UUID {
        dsl.select(CHAT_IMPORT.THREAD_ID)
            .from(CHAT_IMPORT)
            .where(CHAT_IMPORT.MEETING_ID.eq(meetingId))
            .and(CHAT_IMPORT.SOURCE_ROOM_NAME.isNotDistinctFrom(roomName))
            .orderBy(CHAT_IMPORT.CREATED_AT.asc())
            .limit(1)
            .fetchOne(CHAT_IMPORT.THREAD_ID)
            ?.let { return it }

        val nextSortOrder = (
            dsl.select(DSL.max(TOPIC_THREAD.SORT_ORDER))
                .from(TOPIC_THREAD)
                .where(TOPIC_THREAD.MEETING_ID.eq(meetingId))
                .fetchOne(0, Int::class.java) ?: -1
            ) + 1
        val threadId = UUID.randomUUID()
        dsl.insertInto(TOPIC_THREAD)
            .set(TOPIC_THREAD.ID, threadId)
            .set(TOPIC_THREAD.MEETING_ID, meetingId)
            .set(TOPIC_THREAD.CATEGORY, ThreadCategory.free_chat)
            .set(TOPIC_THREAD.TITLE, "Imported: ${roomName ?: "chat"}".take(200))
            .set(TOPIC_THREAD.SORT_ORDER, nextSortOrder)
            .set(TOPIC_THREAD.READ_ONLY, true)
            .execute()
        return threadId
    }

    private fun upsertSpeakers(
        meetingId: UUID,
        threadId: UUID,
        actorId: UUID,
        inputs: List<ValidatedSpeaker>,
    ): Map<String, StoredSpeaker> {
        val existing = dsl.selectFrom(IMPORT_SPEAKER)
            .where(IMPORT_SPEAKER.THREAD_ID.eq(threadId))
            .fetch()
            .associateBy { requireNotNull(it.displayName) }
        var nextIndex = existing.size

        return inputs.associate { input ->
            val membershipId = resolveMembership(meetingId, actorId, input)
            val current = existing[input.displayName]
            val speakerId = if (current != null) {
                if (current.membershipId == null && membershipId != null) {
                    dsl.update(IMPORT_SPEAKER)
                        .set(IMPORT_SPEAKER.MEMBERSHIP_ID, membershipId)
                        .where(IMPORT_SPEAKER.ID.eq(current.id))
                        .execute()
                }
                requireNotNull(current.id)
            } else {
                UUID.randomUUID().also { id ->
                    dsl.insertInto(IMPORT_SPEAKER)
                        .set(IMPORT_SPEAKER.ID, id)
                        .set(IMPORT_SPEAKER.MEETING_ID, meetingId)
                        .set(IMPORT_SPEAKER.THREAD_ID, threadId)
                        .set(IMPORT_SPEAKER.DISPLAY_NAME, input.displayName)
                        .set(IMPORT_SPEAKER.PSEUDONYM, pseudonym(nextIndex++))
                        .set(IMPORT_SPEAKER.MEMBERSHIP_ID, membershipId)
                        .execute()
                }
            }
            input.key to StoredSpeaker(speakerId, input.displayName)
        }
    }

    private fun resolveMembership(meetingId: UUID, actorId: UUID, input: ValidatedSpeaker): UUID? {
        if (input.isSelf) {
            return dsl.select(MEMBERSHIP.ID)
                .from(MEMBERSHIP)
                .where(MEMBERSHIP.MEETING_ID.eq(meetingId))
                .and(MEMBERSHIP.USER_ID.eq(actorId))
                .fetchOne(MEMBERSHIP.ID)
        }
        val membershipId = input.membershipId ?: return null
        val belongs = dsl.fetchExists(
            dsl.selectOne().from(MEMBERSHIP)
                .where(MEMBERSHIP.ID.eq(membershipId))
                .and(MEMBERSHIP.MEETING_ID.eq(meetingId)),
        )
        if (!belongs) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "speaker membershipId does not belong to this meeting")
        }
        return membershipId
    }

    private fun existingHashes(threadId: UUID, hashes: List<String>): Set<String> =
        hashes.distinct().chunked(HASH_LOOKUP_CHUNK).flatMap { chunk ->
            dsl.select(MESSAGE.CONTENT_HASH)
                .from(MESSAGE)
                .where(MESSAGE.THREAD_ID.eq(threadId))
                .and(MESSAGE.CONTENT_HASH.`in`(chunk))
                .fetch(MESSAGE.CONTENT_HASH)
                .filterNotNull()
        }.toSet()

    private fun deletionImpact(importId: UUID): ChatImportDeletionImpact {
        val importMessages = MESSAGE.IMPORT_ID.eq(importId)
        return ChatImportDeletionImpact(
            removedMessages = dsl.fetchCount(MESSAGE, importMessages),
            removedEvidence = dsl.fetchCount(
                dsl.select(DECISION_EVIDENCE.ID)
                    .from(DECISION_EVIDENCE)
                    .join(MESSAGE).on(MESSAGE.ID.eq(DECISION_EVIDENCE.MESSAGE_ID))
                    .where(importMessages),
            ),
            affectedConfirmedDecisions = dsl.fetchCount(
                dsl.selectDistinct(DECISION.ID)
                    .from(DECISION)
                    .join(DECISION_EVIDENCE).on(DECISION_EVIDENCE.DECISION_ID.eq(DECISION.ID))
                    .join(MESSAGE).on(MESSAGE.ID.eq(DECISION_EVIDENCE.MESSAGE_ID))
                    .where(importMessages)
                    .and(DECISION.STATUS.eq(DecisionStatus.confirmed)),
            ),
        )
    }

    private fun toView(record: ChatImportRecord): ChatImportView =
        ChatImportView(
            id = requireNotNull(record.id),
            meetingId = requireNotNull(record.meetingId),
            threadId = requireNotNull(record.threadId),
            uploadedByUserId = requireNotNull(record.uploadedByUserId),
            sourceType = requireNotNull(record.sourceType).literal,
            sourceFormat = requireNotNull(record.sourceFormat),
            sourceRoomName = record.sourceRoomName,
            rangeStart = requireNotNull(record.rangeStart),
            rangeEnd = requireNotNull(record.rangeEnd),
            messageCount = requireNotNull(record.messageCount),
            duplicateCount = requireNotNull(record.duplicateCount),
            status = requireNotNull(record.status).literal,
            progress = jsonToMap(record.progress) ?: emptyMap(),
            result = jsonToMap(record.result),
            failureReason = record.failureReason,
            purgeAt = record.purgeAt,
            createdAt = requireNotNull(record.createdAt),
            completedAt = record.completedAt,
        )

    private fun toJson(value: Any): JSON = JSON.valueOf(objectMapper.writeValueAsString(value))

    private fun jsonToMap(value: JSON?): Map<String, Any?>? {
        if (value == null || value.data().isBlank()) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(value.data(), Map::class.java) as Map<String, Any?>
    }

    private data class StoredSpeaker(val id: UUID, val displayName: String)

    internal companion object {
        const val DEFAULT_MEETING_TITLE = "Imported chat"
        const val DEFAULT_MEETING_TYPE = "outing"
        const val HASH_LOOKUP_CHUNK = 500
        val GUEST_RETENTION: Duration = Duration.ofDays(7)

        fun pseudonym(index: Int): String {
            var remaining = index
            val letters = StringBuilder()
            do {
                letters.insert(0, 'A' + remaining % 26)
                remaining = remaining / 26 - 1
            } while (remaining >= 0)
            return "Participant $letters"
        }
    }
}

internal fun ChatImportSource.isSupportedForUpload(): Boolean =
    this == ChatImportSource.kakao_export || this == ChatImportSource.paste
