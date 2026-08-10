package com.dudecide.app.domain

import com.dudecide.app.api.dto.AddDecisionEvidenceRequest
import com.dudecide.app.api.dto.ConfirmDecisionRequest
import com.dudecide.app.api.dto.CreateDecisionRequest
import com.dudecide.app.api.dto.DecisionDetailResponse
import com.dudecide.app.api.dto.DecisionEvidenceView
import com.dudecide.app.api.dto.DecisionHistoryView
import com.dudecide.app.api.dto.DecisionResponse
import com.dudecide.app.api.dto.ReconsiderDecisionRequest
import com.dudecide.app.jooq.enums.DecisionStatus
import com.dudecide.app.jooq.enums.DecisionType
import com.dudecide.app.jooq.enums.NotificationType
import com.dudecide.app.jooq.tables.DecisionEvidence.Companion.DECISION_EVIDENCE
import com.dudecide.app.jooq.enums.DecisionHistoryAction
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.Meeting.Companion.MEETING
import com.dudecide.app.jooq.tables.DecisionHistory.Companion.DECISION_HISTORY
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
class DecisionService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    private val localActorProvider: LocalActorProvider,
    private val meetingAccessService: MeetingAccessService,
    private val outboxService: OutboxService,
    private val eventStreamService: EventStreamService,
    private val notificationService: NotificationService,
) {

    @Transactional(readOnly = true)
    fun listDecisions(meetingId: UUID): List<DecisionResponse> {
        meetingAccessService.requireMeetingMember(meetingId)
        return dsl.selectFrom(DECISION)
            .where(DECISION.MEETING_ID.eq(meetingId))
            .orderBy(DECISION.CREATED_AT.asc())
            .fetch()
            .map { record ->
                toResponse(record)
            }
    }

    @Transactional
    fun createDecision(meetingId: UUID, request: CreateDecisionRequest): DecisionResponse {
        val actorId = meetingAccessService.requireMeetingHostOrCoHost(meetingId)
        requireMeeting(meetingId)
        requireThread(meetingId, request.threadId)

        val decisionId = UUID.randomUUID()
        val proposedJson = jsonOf(request.proposedResult)

        dsl.insertInto(DECISION)
            .set(DECISION.ID, decisionId)
            .set(DECISION.MEETING_ID, meetingId)
            .set(DECISION.THREAD_ID, request.threadId)
            .set(DECISION.DECISION_TYPE, request.decisionType.toDecisionType())
            .set(DECISION.TITLE, request.title)
            .set(DECISION.STATUS, DecisionStatus.proposed)
            .set(DECISION.PROPOSED_RESULT, proposedJson)
            .set(DECISION.CONFIRMED_RESULT, JSON.valueOf("{}"))
            .execute()

        val record = requireDecision(decisionId)
        val snapshot = decisionSnapshot(record)

        dsl.insertInto(DECISION_HISTORY)
            .set(DECISION_HISTORY.ID, UUID.randomUUID())
            .set(DECISION_HISTORY.DECISION_ID, decisionId)
            .set(DECISION_HISTORY.ACTOR_USER_ID, actorId)
            .set(DECISION_HISTORY.ACTION, DecisionHistoryAction.drafted)
            .set(DECISION_HISTORY.BEFORE_STATE, JSON.valueOf("{}"))
            .set(DECISION_HISTORY.AFTER_STATE, jsonOf(snapshot))
            .execute()

        outboxService.append(
            aggregateType = "decision",
            aggregateId = decisionId,
            eventType = "DecisionDrafted",
            payload = mapOf(
                "decisionId" to decisionId,
                "meetingId" to meetingId,
                "threadId" to request.threadId,
                "decisionType" to request.decisionType,
            ),
        )
        eventStreamService.publish(
            meetingId = meetingId,
            eventType = "decision.updated",
            payload = mapOf(
                "meetingId" to meetingId,
                "decisionId" to decisionId,
                "status" to "proposed",
            ),
        )

        return toResponse(record)
    }

    @Transactional
    fun confirmDecision(decisionId: UUID, request: ConfirmDecisionRequest): DecisionResponse {
        val actorId = meetingAccessService.requireDecisionHostOrCoHost(decisionId)
        val now = OffsetDateTime.now()

        val record = requireDecision(decisionId)

        val confirmedJson = JSON.valueOf(objectMapper.writeValueAsString(request.confirmedResult))
        val proposedJson = record.proposedResult ?: JSON.valueOf("{}")

        dsl.update(DECISION)
            .set(DECISION.STATUS, DecisionStatus.confirmed)
            .set(DECISION.CONFIRMED_RESULT, confirmedJson)
            .set(DECISION.DECIDED_AT, now)
            .set(DECISION.DECIDED_BY_USER_ID, actorId)
            .where(DECISION.ID.eq(decisionId))
            .execute()

        dsl.insertInto(DECISION_HISTORY)
            .set(DECISION_HISTORY.ID, UUID.randomUUID())
            .set(DECISION_HISTORY.DECISION_ID, decisionId)
            .set(DECISION_HISTORY.ACTOR_USER_ID, actorId)
            .set(DECISION_HISTORY.ACTION, DecisionHistoryAction.confirmed)
            .set(DECISION_HISTORY.BEFORE_STATE, proposedJson)
            .set(DECISION_HISTORY.AFTER_STATE, confirmedJson)
            .set(DECISION_HISTORY.REASON, request.reason)
            .execute()

        outboxService.append(
            aggregateType = "decision",
            aggregateId = decisionId,
            eventType = "DecisionConfirmed",
            payload = mapOf(
                "decisionId" to decisionId,
                "meetingId" to requireNotNull(record.meetingId),
                "threadId" to requireNotNull(record.threadId),
                "actorUserId" to actorId,
                "reason" to request.reason,
            ),
        )
        eventStreamService.publish(
            meetingId = requireNotNull(record.meetingId),
            eventType = "decision.updated",
            payload = mapOf(
                "meetingId" to requireNotNull(record.meetingId),
                "decisionId" to decisionId,
                "status" to "confirmed",
            ),
        )
        notificationService.createNotification(
            meetingId = requireNotNull(record.meetingId),
            type = NotificationType.decision_confirmed,
            payload = mapOf(
                "decisionId" to decisionId,
                "title" to requireNotNull(record.title),
            ),
        )

        return toResponse(requireDecision(decisionId))
    }

    @Transactional
    fun reconsiderDecision(decisionId: UUID, request: ReconsiderDecisionRequest): DecisionResponse {
        val actorId = meetingAccessService.requireDecisionHostOrCoHost(decisionId)
        val record = requireDecision(decisionId)

        if (record.status != DecisionStatus.confirmed) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "only confirmed decisions can be reconsidered")
        }

        val beforeState = decisionSnapshot(record)

        dsl.update(DECISION)
            .set(DECISION.STATUS, DecisionStatus.needs_reconsideration)
            .where(DECISION.ID.eq(decisionId))
            .execute()

        val updatedRecord = requireDecision(decisionId)
        val afterState = decisionSnapshot(updatedRecord)

        dsl.insertInto(DECISION_HISTORY)
            .set(DECISION_HISTORY.ID, UUID.randomUUID())
            .set(DECISION_HISTORY.DECISION_ID, decisionId)
            .set(DECISION_HISTORY.ACTOR_USER_ID, actorId)
            .set(DECISION_HISTORY.ACTION, DecisionHistoryAction.reopened)
            .set(DECISION_HISTORY.BEFORE_STATE, jsonOf(beforeState))
            .set(DECISION_HISTORY.AFTER_STATE, jsonOf(afterState))
            .set(DECISION_HISTORY.REASON, request.reason)
            .execute()

        outboxService.append(
            aggregateType = "decision",
            aggregateId = decisionId,
            eventType = "DecisionReconsiderationRequested",
            payload = mapOf(
                "decisionId" to decisionId,
                "meetingId" to requireNotNull(record.meetingId),
                "threadId" to requireNotNull(record.threadId),
                "actorUserId" to actorId,
                "reason" to request.reason,
            ),
        )
        eventStreamService.publish(
            meetingId = requireNotNull(record.meetingId),
            eventType = "decision.updated",
            payload = mapOf(
                "meetingId" to requireNotNull(record.meetingId),
                "decisionId" to decisionId,
                "status" to "needs_reconsideration",
            ),
        )

        return toResponse(updatedRecord)
    }

    @Transactional
    fun addEvidence(decisionId: UUID, request: AddDecisionEvidenceRequest): DecisionEvidenceView {
        meetingAccessService.requireDecisionHostOrCoHost(decisionId)
        val record = requireDecision(decisionId)
        val message = dsl.selectFrom(MESSAGE)
            .where(MESSAGE.ID.eq(request.messageId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "message not found")

        if (message.threadId != record.threadId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "message must belong to the same thread as the decision")
        }

        val evidenceId = UUID.randomUUID()
        val createdAt = OffsetDateTime.now()
        val quoteSnapshot = request.quoteSnapshot ?: requireNotNull(message.body).take(280)
        val evidenceType = request.evidenceType.toEvidenceType()

        dsl.insertInto(DECISION_EVIDENCE)
            .set(DECISION_EVIDENCE.ID, evidenceId)
            .set(DECISION_EVIDENCE.DECISION_ID, decisionId)
            .set(DECISION_EVIDENCE.MESSAGE_ID, request.messageId)
            .set(DECISION_EVIDENCE.THREAD_ID, requireNotNull(record.threadId))
            .set(DECISION_EVIDENCE.EVIDENCE_TYPE, evidenceType)
            .set(DECISION_EVIDENCE.QUOTE_SNAPSHOT, quoteSnapshot)
            .set(DECISION_EVIDENCE.CONFIDENCE, request.confidence?.toBigDecimal())
            .set(DECISION_EVIDENCE.CREATED_AT, createdAt)
            .execute()

        outboxService.append(
            aggregateType = "decision",
            aggregateId = decisionId,
            eventType = "DecisionEvidenceLinked",
            payload = mapOf(
                "decisionId" to decisionId,
                "meetingId" to requireNotNull(record.meetingId),
                "messageId" to request.messageId,
                "evidenceType" to evidenceType.literal,
            ),
        )
        eventStreamService.publish(
            meetingId = requireNotNull(record.meetingId),
            eventType = "decision.updated",
            payload = mapOf(
                "meetingId" to requireNotNull(record.meetingId),
                "decisionId" to decisionId,
                "messageId" to request.messageId,
                "evidenceType" to evidenceType.literal,
            ),
        )

        return DecisionEvidenceView(
            id = evidenceId,
            messageId = request.messageId,
            threadId = requireNotNull(record.threadId),
            evidenceType = evidenceType.literal,
            quoteSnapshot = quoteSnapshot,
            confidence = request.confidence,
            createdAt = createdAt,
        )
    }

    @Transactional(readOnly = true)
    fun getDecision(decisionId: UUID): DecisionDetailResponse {
        meetingAccessService.requireDecisionMember(decisionId)
        val record = requireDecision(decisionId)

        val evidence = dsl.selectFrom(DECISION_EVIDENCE)
            .where(DECISION_EVIDENCE.DECISION_ID.eq(decisionId))
            .orderBy(DECISION_EVIDENCE.CREATED_AT.asc())
            .fetch()
            .map { item ->
                DecisionEvidenceView(
                    id = requireNotNull(item.id),
                    messageId = requireNotNull(item.messageId),
                    threadId = requireNotNull(item.threadId),
                    evidenceType = requireNotNull(item.evidenceType).literal,
                    quoteSnapshot = requireNotNull(item.quoteSnapshot),
                    confidence = item.confidence?.toDouble(),
                    createdAt = item.createdAt,
                )
            }

        val history = dsl.selectFrom(DECISION_HISTORY)
            .where(DECISION_HISTORY.DECISION_ID.eq(decisionId))
            .orderBy(DECISION_HISTORY.CREATED_AT.asc())
            .fetch()
            .map { item ->
                DecisionHistoryView(
                    id = requireNotNull(item.id),
                    actorUserId = item.actorUserId,
                    action = requireNotNull(item.action).literal,
                    reason = item.reason,
                    beforeState = jsonToMap(item.beforeState),
                    afterState = jsonToMap(item.afterState),
                    createdAt = item.createdAt,
                )
            }

        return DecisionDetailResponse(
            id = requireNotNull(record.id),
            meetingId = requireNotNull(record.meetingId),
            threadId = requireNotNull(record.threadId),
            decisionType = requireNotNull(record.decisionType).literal,
            title = requireNotNull(record.title),
            status = com.dudecide.shared.decision.DecisionStatus.valueOf(requireNotNull(record.status).literal.uppercase()),
            proposedResult = jsonToMap(record.proposedResult),
            confirmedResult = jsonToMap(record.confirmedResult),
            decidedAt = record.decidedAt,
            evidence = evidence,
            history = history,
        )
    }

    private fun requireDecision(decisionId: UUID) =
        dsl.selectFrom(DECISION)
            .where(DECISION.ID.eq(decisionId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "decision not found")

    private fun requireMeeting(meetingId: UUID) {
        val exists = dsl.fetchExists(
            dsl.selectOne()
                .from(MEETING)
                .where(MEETING.ID.eq(meetingId)),
        )
        if (!exists) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "meeting not found")
        }
    }

    private fun requireThread(meetingId: UUID, threadId: UUID) {
        val exists = dsl.fetchExists(
            dsl.selectOne()
                .from(TOPIC_THREAD)
                .where(TOPIC_THREAD.ID.eq(threadId))
                .and(TOPIC_THREAD.MEETING_ID.eq(meetingId)),
        )
        if (!exists) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "thread does not belong to the meeting")
        }
    }

    private fun decisionSnapshot(record: com.dudecide.app.jooq.tables.records.DecisionRecord): Map<String, Any?> =
        mapOf(
            "status" to requireNotNull(record.status).literal,
            "title" to requireNotNull(record.title),
            "decisionType" to requireNotNull(record.decisionType).literal,
            "proposedResult" to jsonToMap(record.proposedResult),
            "confirmedResult" to jsonToMap(record.confirmedResult),
            "decidedAt" to record.decidedAt?.toString(),
        )

    private fun toResponse(record: com.dudecide.app.jooq.tables.records.DecisionRecord) =
        DecisionResponse(
            id = requireNotNull(record.id),
            meetingId = requireNotNull(record.meetingId),
            threadId = requireNotNull(record.threadId),
            decisionType = requireNotNull(record.decisionType).literal,
            title = requireNotNull(record.title),
            status = com.dudecide.shared.decision.DecisionStatus.valueOf(requireNotNull(record.status).literal.uppercase()),
            proposedResult = jsonToMap(record.proposedResult),
            confirmedResult = jsonToMap(record.confirmedResult),
            decidedAt = record.decidedAt,
        )

    private fun String.toDecisionType(): DecisionType =
        runCatching { DecisionType.valueOf(lowercase()) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported decision type: $this") }

    private fun String.toEvidenceType(): com.dudecide.app.jooq.enums.EvidenceType =
        runCatching { com.dudecide.app.jooq.enums.EvidenceType.valueOf(lowercase()) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported evidence type: $this") }

    private fun jsonOf(value: Map<String, Any?>): JSON =
        JSON.valueOf(objectMapper.writeValueAsString(value))

    private fun Double.toBigDecimal(): java.math.BigDecimal = java.math.BigDecimal.valueOf(this)

    private fun jsonToMap(value: JSON?): Map<String, Any?> {
        if (value == null || value.data().isBlank()) {
            return emptyMap()
        }

        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(value.data(), Map::class.java) as Map<String, Any?>
    }
}
