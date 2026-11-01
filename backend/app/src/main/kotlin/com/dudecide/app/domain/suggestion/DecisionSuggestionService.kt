package com.dudecide.app.domain.suggestion

import com.dudecide.app.api.dto.DecisionResponse
import com.dudecide.app.api.dto.DecisionSuggestionEvidence
import com.dudecide.app.api.dto.DecisionSuggestionView
import com.dudecide.app.domain.EventStreamService
import com.dudecide.app.domain.MeetingAccessService
import com.dudecide.app.domain.OutboxService
import com.dudecide.app.jooq.enums.DecisionHistoryAction
import com.dudecide.app.jooq.enums.DecisionStatus
import com.dudecide.app.jooq.enums.EvidenceType
import com.dudecide.app.jooq.tables.AppUser.Companion.APP_USER
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.DecisionEvidence.Companion.DECISION_EVIDENCE
import com.dudecide.app.jooq.tables.DecisionHistory.Companion.DECISION_HISTORY
import com.dudecide.app.jooq.tables.DecisionSuggestion.Companion.DECISION_SUGGESTION
import com.dudecide.app.jooq.tables.ImportSpeaker.Companion.IMPORT_SPEAKER
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.Message.Companion.MESSAGE
import com.dudecide.app.jooq.tables.records.DecisionSuggestionRecord
import com.dudecide.shared.decision.DecisionStatus as SharedDecisionStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.DSLContext
import org.jooq.JSON
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.UUID

/** Lists pending live suggestions and lets a host turn one into a decision draft or dismiss it. */
@Service
class DecisionSuggestionService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    private val meetingAccessService: MeetingAccessService,
    private val outboxService: OutboxService,
    private val eventStreamService: EventStreamService,
) {

    @Transactional(readOnly = true)
    fun listPending(threadId: UUID): List<DecisionSuggestionView> {
        meetingAccessService.requireThreadMember(threadId)
        return dsl.selectFrom(DECISION_SUGGESTION)
            .where(DECISION_SUGGESTION.THREAD_ID.eq(threadId))
            .and(DECISION_SUGGESTION.STATUS.eq(PENDING))
            .orderBy(DECISION_SUGGESTION.CREATED_AT.asc())
            .fetch()
            .map(::toView)
    }

    @Transactional
    fun accept(suggestionId: UUID): DecisionResponse {
        val suggestion = requirePending(suggestionId)
        val actorId = meetingAccessService.requireThreadHostOrCoHost(requireNotNull(suggestion.threadId))
        val meetingId = requireNotNull(suggestion.meetingId)
        val threadId = requireNotNull(suggestion.threadId)
        val likelyDecided = suggestion.analysisStatus == "likely_decided"
        val status = if (likelyDecided) DecisionStatus.proposed else DecisionStatus.in_discussion
        val proposedResult = readMap(suggestion.proposedResult) + summaryOf(readMap(suggestion.proposedResult)) +
            mapOf("source" to "live_suggestion", "suggestionId" to suggestionId.toString())
        val title = requireNotNull(suggestion.title)
        val decisionType = requireNotNull(suggestion.decisionType)
        val decisionId = UUID.randomUUID()

        dsl.insertInto(DECISION)
            .set(DECISION.ID, decisionId)
            .set(DECISION.MEETING_ID, meetingId)
            .set(DECISION.THREAD_ID, threadId)
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
            .set(DECISION_HISTORY.REASON, "Accepted from AI suggestion")
            .execute()
        attachEvidence(decisionId, evidenceOf(suggestion))

        dsl.update(DECISION_SUGGESTION)
            .set(DECISION_SUGGESTION.STATUS, ACCEPTED)
            .set(DECISION_SUGGESTION.DECISION_ID, decisionId)
            .set(DECISION_SUGGESTION.RESOLVED_BY_USER_ID, actorId)
            .set(DECISION_SUGGESTION.RESOLVED_AT, OffsetDateTime.now())
            .where(DECISION_SUGGESTION.ID.eq(suggestionId))
            .execute()

        outboxService.append(
            aggregateType = "decision",
            aggregateId = decisionId,
            eventType = "DecisionDrafted",
            payload = mapOf(
                "decisionId" to decisionId,
                "meetingId" to meetingId,
                "decisionType" to decisionType.literal,
                "source" to "live_suggestion",
                "suggestionId" to suggestionId,
            ),
        )
        eventStreamService.publish(
            meetingId = meetingId,
            eventType = "decision.updated",
            payload = mapOf("meetingId" to meetingId, "decisionId" to decisionId, "status" to status.literal),
        )
        publishThreadUpdate(meetingId, threadId)

        return DecisionResponse(
            id = decisionId,
            meetingId = meetingId,
            threadId = threadId,
            decisionType = decisionType.literal,
            title = title,
            status = SharedDecisionStatus.valueOf(status.literal.uppercase()),
            proposedResult = proposedResult,
        )
    }

    @Transactional
    fun dismiss(suggestionId: UUID): DecisionSuggestionView {
        val suggestion = requirePending(suggestionId)
        val actorId = meetingAccessService.requireThreadHostOrCoHost(requireNotNull(suggestion.threadId))
        dsl.update(DECISION_SUGGESTION)
            .set(DECISION_SUGGESTION.STATUS, DISMISSED)
            .set(DECISION_SUGGESTION.RESOLVED_BY_USER_ID, actorId)
            .set(DECISION_SUGGESTION.RESOLVED_AT, OffsetDateTime.now())
            .where(DECISION_SUGGESTION.ID.eq(suggestionId))
            .execute()
        publishThreadUpdate(requireNotNull(suggestion.meetingId), requireNotNull(suggestion.threadId))
        return toView(requireNotNull(dsl.selectFrom(DECISION_SUGGESTION).where(DECISION_SUGGESTION.ID.eq(suggestionId)).fetchOne()))
    }

    private fun requirePending(suggestionId: UUID): DecisionSuggestionRecord {
        val suggestion = dsl.selectFrom(DECISION_SUGGESTION).where(DECISION_SUGGESTION.ID.eq(suggestionId)).fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "decision suggestion not found")
        meetingAccessService.requireThreadMember(requireNotNull(suggestion.threadId))
        if (suggestion.status != PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "decision suggestion was already ${suggestion.status}")
        }
        return suggestion
    }

    // Quotes carry the label every member may see: the member name, a mapped import speaker's name, or the pseudonym.
    private fun attachEvidence(decisionId: UUID, evidence: List<DecisionSuggestionEvidence>) {
        val messageIds = evidence.map { it.messageId }
        if (messageIds.isEmpty()) return
        val sender = APP_USER.`as`("sender")
        val mapped = APP_USER.`as`("mapped")
        val quotes = dsl.select(MESSAGE.ID, MESSAGE.THREAD_ID, MESSAGE.BODY, sender.DISPLAY_NAME, mapped.DISPLAY_NAME, IMPORT_SPEAKER.PSEUDONYM)
            .from(MESSAGE)
            .leftJoin(sender).on(sender.ID.eq(MESSAGE.SENDER_USER_ID))
            .leftJoin(IMPORT_SPEAKER).on(IMPORT_SPEAKER.ID.eq(MESSAGE.IMPORT_SPEAKER_ID))
            .leftJoin(MEMBERSHIP).on(MEMBERSHIP.ID.eq(IMPORT_SPEAKER.MEMBERSHIP_ID))
            .leftJoin(mapped).on(mapped.ID.eq(MEMBERSHIP.USER_ID))
            .where(MESSAGE.ID.`in`(messageIds))
            .fetch()
            .associateBy { requireNotNull(it.get(MESSAGE.ID)) }

        evidence.mapNotNull { entry ->
            val type = EVIDENCE_TYPES[entry.role] ?: return@mapNotNull null
            val quote = quotes[entry.messageId] ?: return@mapNotNull null
            Triple(entry.messageId, type, quote)
        }.distinctBy { it.first to it.second }.forEach { (messageId, type, quote) ->
            val label = quote.get(sender.DISPLAY_NAME) ?: quote.get(mapped.DISPLAY_NAME) ?: quote.get(IMPORT_SPEAKER.PSEUDONYM) ?: ""
            dsl.insertInto(DECISION_EVIDENCE)
                .set(DECISION_EVIDENCE.ID, UUID.randomUUID())
                .set(DECISION_EVIDENCE.DECISION_ID, decisionId)
                .set(DECISION_EVIDENCE.MESSAGE_ID, messageId)
                .set(DECISION_EVIDENCE.THREAD_ID, requireNotNull(quote.get(MESSAGE.THREAD_ID)))
                .set(DECISION_EVIDENCE.EVIDENCE_TYPE, type)
                .set(DECISION_EVIDENCE.QUOTE_SNAPSHOT, "$label: ${quote.get(MESSAGE.BODY)}".take(QUOTE_LENGTH))
                .execute()
        }
    }

    private fun summaryOf(proposedResult: Map<String, Any?>): Map<String, Any?> =
        (proposedResult["option"] as? String)?.takeIf { it.isNotBlank() }?.let { mapOf("summary" to it) }.orEmpty()

    private fun toView(record: DecisionSuggestionRecord) = DecisionSuggestionView(
        id = requireNotNull(record.id),
        meetingId = requireNotNull(record.meetingId),
        threadId = requireNotNull(record.threadId),
        status = requireNotNull(record.status),
        analysisStatus = requireNotNull(record.analysisStatus),
        decisionType = requireNotNull(record.decisionType).literal,
        title = requireNotNull(record.title),
        proposedResult = readMap(record.proposedResult),
        evidence = evidenceOf(record),
        decisionId = record.decisionId,
        createdAt = requireNotNull(record.createdAt),
    )

    private fun evidenceOf(record: DecisionSuggestionRecord): List<DecisionSuggestionEvidence> {
        val data = record.evidence?.data()?.takeIf { it.isNotBlank() } ?: return emptyList()
        return objectMapper.readTree(data).mapNotNull { node ->
            val messageId = node.path("messageId").asText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DecisionSuggestionEvidence(UUID.fromString(messageId), node.path("role").asText())
        }
    }

    private fun publishThreadUpdate(meetingId: UUID, threadId: UUID) {
        eventStreamService.publish(
            meetingId = meetingId,
            eventType = "thread.updated",
            payload = mapOf("meetingId" to meetingId, "threadId" to threadId, "kind" to "suggestion.resolved"),
        )
    }

    private fun readMap(value: JSON?): Map<String, Any?> {
        if (value == null || value.data().isBlank()) return emptyMap()
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(value.data(), Map::class.java) as Map<String, Any?>
    }

    private fun toJson(value: Any): JSON = JSON.valueOf(objectMapper.writeValueAsString(value))

    private companion object {
        const val PENDING = "pending"
        const val ACCEPTED = "accepted"
        const val DISMISSED = "dismissed"
        const val QUOTE_LENGTH = 280

        val EVIDENCE_TYPES = mapOf(
            "proposal" to EvidenceType.proposal,
            "support" to EvidenceType.support,
            "opposition" to EvidenceType.opposition,
            "confirmation" to EvidenceType.confirmation,
            "condition" to EvidenceType.condition,
        )
    }
}
