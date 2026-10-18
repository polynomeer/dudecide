package com.dudecide.app.domain.chatimport

import com.dudecide.app.api.dto.ApplyChatImportRequest
import com.dudecide.app.api.dto.ApplyChatImportResponse
import com.dudecide.app.api.dto.CreateTaskRequest
import com.dudecide.app.domain.EventStreamService
import com.dudecide.app.domain.MeetingAccessService
import com.dudecide.app.domain.OutboxService
import com.dudecide.app.domain.ReportService
import com.dudecide.app.domain.TaskService
import com.dudecide.app.jooq.enums.ChatImportStatus
import com.dudecide.app.jooq.enums.DecisionHistoryAction
import com.dudecide.app.jooq.enums.DecisionStatus
import com.dudecide.app.jooq.enums.DecisionType
import com.dudecide.app.jooq.enums.EvidenceType
import com.dudecide.app.jooq.enums.MeetingStatus
import com.dudecide.app.jooq.enums.ThreadCategory
import com.dudecide.app.jooq.tables.AppUser.Companion.APP_USER
import com.dudecide.app.jooq.tables.ChatImport.Companion.CHAT_IMPORT
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.DecisionEvidence.Companion.DECISION_EVIDENCE
import com.dudecide.app.jooq.tables.DecisionHistory.Companion.DECISION_HISTORY
import com.dudecide.app.jooq.tables.ImportSpeaker.Companion.IMPORT_SPEAKER
import com.dudecide.app.jooq.tables.Meeting.Companion.MEETING
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.Message.Companion.MESSAGE
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.DSLContext
import org.jooq.JSON
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.UUID

/**
 * Materializes selected staged items into board objects. This is the human gate of ADR 0003:
 * nothing from the analysis reaches decisions or tasks until a host applies it, and nothing is confirmed here.
 */
@Service
class ChatImportApplyService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    private val meetingAccessService: MeetingAccessService,
    private val outboxService: OutboxService,
    private val eventStreamService: EventStreamService,
    private val taskService: TaskService,
    private val reportService: ReportService,
) {

    @Transactional
    fun apply(importId: UUID, request: ApplyChatImportRequest): ApplyChatImportResponse {
        val record = dsl.selectFrom(CHAT_IMPORT).where(CHAT_IMPORT.ID.eq(importId)).forUpdate().fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "import not found")
        val meetingId = requireNotNull(record.meetingId)
        val actorId = meetingAccessService.requireMeetingHostOrCoHost(meetingId)
        if (record.status != ChatImportStatus.ready && record.status != ChatImportStatus.applied) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "import is not ready to apply")
        }
        if (request.itemIds.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "itemIds must not be empty")
        }

        val result = readMap(record.result)
        @Suppress("UNCHECKED_CAST")
        val items = (result["items"] as? List<Map<String, Any?>>).orEmpty().associateBy { it["itemId"] as String }
        @Suppress("UNCHECKED_CAST")
        val alreadyApplied = (result["appliedItemIds"] as? List<String>).orEmpty().toSet()

        val requested = request.itemIds.distinct()
        requested.firstOrNull { it !in items }?.let {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown item: $it")
        }
        val toApply = requested.filterNot { it in alreadyApplied }.map { items.getValue(it) }
        toApply.firstOrNull { !isApplicable(it) }?.let {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "item cannot be applied: ${it["itemId"]}")
        }

        val labels = speakerLabels(importId)
        val decisionIds = mutableListOf<UUID>()
        val taskIds = mutableListOf<UUID>()
        toApply.forEach { item ->
            when (item["kind"]) {
                "decision" -> decisionIds += createDecision(meetingId, actorId, importId, item, labels)
                "task" -> taskIds += createTask(meetingId, item)
            }
        }

        val appliedIds = toApply.map { it["itemId"] as String }
        dsl.update(CHAT_IMPORT)
            .set(CHAT_IMPORT.STATUS, ChatImportStatus.applied)
            .set(CHAT_IMPORT.RESULT, toJson(result + ("appliedItemIds" to (alreadyApplied.toList() + appliedIds))))
            .where(CHAT_IMPORT.ID.eq(importId))
            .execute()
        val meetingStatus = activateMeeting(meetingId)

        if (appliedIds.isNotEmpty()) {
            outboxService.append(
                aggregateType = "chat_import",
                aggregateId = importId,
                eventType = "ChatImportApplied",
                payload = mapOf(
                    "importId" to importId,
                    "meetingId" to meetingId,
                    "itemIds" to appliedIds,
                    "decisionIds" to decisionIds,
                    "taskIds" to taskIds,
                ),
            )
            reportService.refreshReport(meetingId)
        }

        return ApplyChatImportResponse(
            importId = importId,
            appliedItemIds = appliedIds,
            skippedItemIds = requested.filter { it in alreadyApplied },
            decisionIds = decisionIds,
            taskIds = taskIds,
            meetingStatus = meetingStatus.literal,
        )
    }

    private fun isApplicable(item: Map<String, Any?>): Boolean =
        when (item["kind"]) {
            "decision" -> item["applicable"] != false
            "task" -> true
            else -> false
        }

    private fun createDecision(
        meetingId: UUID,
        actorId: UUID,
        importId: UUID,
        item: Map<String, Any?>,
        labels: Map<UUID, MessageQuote>,
    ): UUID {
        val decisionType = DecisionType.entries.firstOrNull { it.literal == item["decisionType"] } ?: DecisionType.other
        val likelyDecided = item["status"] == "likely_decided"
        val status = if (likelyDecided) DecisionStatus.proposed else DecisionStatus.in_discussion
        val source = mapOf("source" to "chat_import", "importId" to importId.toString(), "itemId" to item["itemId"])
        @Suppress("UNCHECKED_CAST")
        val proposedResult = if (likelyDecided) {
            (item["proposedResult"] as? Map<String, Any?>).orEmpty() + source
        } else {
            mapOf("options" to item["options"]) + source
        }
        val title = (item["title"] as? String).orEmpty().ifBlank { "Imported decision" }.take(200)
        val decisionId = UUID.randomUUID()

        dsl.insertInto(DECISION)
            .set(DECISION.ID, decisionId)
            .set(DECISION.MEETING_ID, meetingId)
            .set(DECISION.THREAD_ID, boardThread(meetingId, decisionType))
            .set(DECISION.DECISION_TYPE, decisionType)
            .set(DECISION.TITLE, title)
            .set(DECISION.STATUS, status)
            .set(DECISION.PROPOSED_RESULT, toJson(proposedResult))
            .set(DECISION.CONFIRMED_RESULT, JSON.valueOf("{}"))
            .execute()
        dsl.insertInto(DECISION_HISTORY)
            .set(DECISION_HISTORY.ID, UUID.randomUUID())
            .set(DECISION_HISTORY.DECISION_ID, decisionId)
            .set(DECISION_HISTORY.ACTOR_USER_ID, actorId)
            .set(DECISION_HISTORY.ACTION, DecisionHistoryAction.drafted)
            .set(DECISION_HISTORY.BEFORE_STATE, JSON.valueOf("{}"))
            .set(DECISION_HISTORY.AFTER_STATE, toJson(mapOf("title" to title, "status" to status.literal, "proposedResult" to proposedResult)))
            .set(DECISION_HISTORY.REASON, "Applied from chat import")
            .execute()

        val confidence = (item["confidence"] as? Number)?.toDouble()?.let { BigDecimal.valueOf(it) }
        @Suppress("UNCHECKED_CAST")
        val evidence = (item["evidence"] as? List<Map<String, Any?>>).orEmpty()
        evidence.mapNotNull { entry ->
            val type = EVIDENCE_TYPES[entry["role"]] ?: return@mapNotNull null
            val quote = labels[UUID.fromString(entry["messageId"] as String)] ?: return@mapNotNull null
            type to quote
        }.distinct().forEach { (type, quote) ->
            dsl.insertInto(DECISION_EVIDENCE)
                .set(DECISION_EVIDENCE.ID, UUID.randomUUID())
                .set(DECISION_EVIDENCE.DECISION_ID, decisionId)
                .set(DECISION_EVIDENCE.MESSAGE_ID, quote.messageId)
                .set(DECISION_EVIDENCE.THREAD_ID, quote.threadId)
                .set(DECISION_EVIDENCE.EVIDENCE_TYPE, type)
                .set(DECISION_EVIDENCE.QUOTE_SNAPSHOT, "${quote.label}: ${quote.body}".take(QUOTE_LENGTH))
                .set(DECISION_EVIDENCE.CONFIDENCE, confidence)
                .execute()
        }

        outboxService.append(
            aggregateType = "decision",
            aggregateId = decisionId,
            eventType = "DecisionDrafted",
            payload = mapOf(
                "decisionId" to decisionId,
                "meetingId" to meetingId,
                "decisionType" to decisionType.literal,
                "source" to "chat_import",
                "importId" to importId,
            ),
        )
        eventStreamService.publish(
            meetingId = meetingId,
            eventType = "decision.updated",
            payload = mapOf("meetingId" to meetingId, "decisionId" to decisionId, "status" to status.literal),
        )
        return decisionId
    }

    private fun createTask(meetingId: UUID, item: Map<String, Any?>): UUID {
        @Suppress("UNCHECKED_CAST")
        val sourceMessageId = (item["evidence"] as? List<Map<String, Any?>>)
            ?.firstOrNull()
            ?.let { UUID.fromString(it["messageId"] as String) }
        val ownerUserId = (item["ownerSpeakerId"] as? String)?.let { speakerId ->
            dsl.select(MEMBERSHIP.USER_ID)
                .from(IMPORT_SPEAKER)
                .join(MEMBERSHIP).on(MEMBERSHIP.ID.eq(IMPORT_SPEAKER.MEMBERSHIP_ID))
                .where(IMPORT_SPEAKER.ID.eq(UUID.fromString(speakerId)))
                .fetchOne(MEMBERSHIP.USER_ID)
        }
        return taskService.createTask(
            meetingId,
            CreateTaskRequest(
                title = (item["title"] as? String).orEmpty().ifBlank { "Imported task" }.take(200),
                ownerUserId = ownerUserId,
                sourceMessageId = sourceMessageId,
            ),
        ).id
    }

    // Decisions live on the board threads; budget and other types fall back to the default free chat thread.
    private fun boardThread(meetingId: UUID, decisionType: DecisionType): UUID {
        val category = THREAD_CATEGORIES[decisionType] ?: ThreadCategory.free_chat
        return listOf(category, ThreadCategory.free_chat).firstNotNullOfOrNull { candidate ->
            dsl.select(TOPIC_THREAD.ID)
                .from(TOPIC_THREAD)
                .where(TOPIC_THREAD.MEETING_ID.eq(meetingId))
                .and(TOPIC_THREAD.CATEGORY.eq(candidate))
                .and(TOPIC_THREAD.READ_ONLY.isFalse)
                .orderBy(TOPIC_THREAD.SORT_ORDER.asc())
                .limit(1)
                .fetchOne(TOPIC_THREAD.ID)
        } ?: throw ResponseStatusException(HttpStatus.CONFLICT, "meeting has no writable thread for $decisionType")
    }

    // Quotes use the label every member may see: the mapped member name, otherwise the pseudonym.
    private fun speakerLabels(importId: UUID): Map<UUID, MessageQuote> =
        dsl.select(MESSAGE.ID, MESSAGE.THREAD_ID, MESSAGE.BODY, IMPORT_SPEAKER.PSEUDONYM, APP_USER.DISPLAY_NAME)
            .from(MESSAGE)
            .join(IMPORT_SPEAKER).on(IMPORT_SPEAKER.ID.eq(MESSAGE.IMPORT_SPEAKER_ID))
            .leftJoin(MEMBERSHIP).on(MEMBERSHIP.ID.eq(IMPORT_SPEAKER.MEMBERSHIP_ID))
            .leftJoin(APP_USER).on(APP_USER.ID.eq(MEMBERSHIP.USER_ID))
            .where(MESSAGE.IMPORT_ID.eq(importId))
            .fetch()
            .associate { record ->
                val messageId = requireNotNull(record.get(MESSAGE.ID))
                messageId to MessageQuote(
                    messageId = messageId,
                    threadId = requireNotNull(record.get(MESSAGE.THREAD_ID)),
                    label = record.get(APP_USER.DISPLAY_NAME) ?: requireNotNull(record.get(IMPORT_SPEAKER.PSEUDONYM)),
                    body = requireNotNull(record.get(MESSAGE.BODY)),
                )
            }

    private fun activateMeeting(meetingId: UUID): MeetingStatus {
        val activated = dsl.update(MEETING)
            .set(MEETING.STATUS, MeetingStatus.active)
            .where(MEETING.ID.eq(meetingId))
            .and(MEETING.STATUS.eq(MeetingStatus.draft))
            .execute() == 1
        dsl.update(CHAT_IMPORT)
            .setNull(CHAT_IMPORT.PURGE_AT)
            .where(CHAT_IMPORT.MEETING_ID.eq(meetingId))
            .execute()
        if (activated) {
            eventStreamService.publish(
                meetingId = meetingId,
                eventType = "meeting.updated",
                payload = mapOf("meetingId" to meetingId, "status" to MeetingStatus.active.literal),
            )
        }
        return requireNotNull(dsl.select(MEETING.STATUS).from(MEETING).where(MEETING.ID.eq(meetingId)).fetchOne(MEETING.STATUS))
    }

    private fun readMap(value: JSON?): Map<String, Any?> {
        if (value == null || value.data().isBlank()) return emptyMap()
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(value.data(), Map::class.java) as Map<String, Any?>
    }

    private fun toJson(value: Any): JSON = JSON.valueOf(objectMapper.writeValueAsString(value))

    private data class MessageQuote(val messageId: UUID, val threadId: UUID, val label: String, val body: String)

    private companion object {
        const val QUOTE_LENGTH = 280

        val EVIDENCE_TYPES = mapOf(
            "proposal" to EvidenceType.proposal,
            "support" to EvidenceType.support,
            "opposition" to EvidenceType.opposition,
            "confirmation" to EvidenceType.confirmation,
            "condition" to EvidenceType.condition,
        )

        val THREAD_CATEGORIES = mapOf(
            DecisionType.date to ThreadCategory.date,
            DecisionType.location to ThreadCategory.location,
            DecisionType.lodging to ThreadCategory.lodging,
            DecisionType.transport to ThreadCategory.transport,
        )
    }
}
