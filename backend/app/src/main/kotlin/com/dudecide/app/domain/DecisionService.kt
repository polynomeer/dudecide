package com.dudecide.app.domain

import com.dudecide.app.api.dto.ConfirmDecisionRequest
import com.dudecide.app.api.dto.DecisionResponse
import com.dudecide.app.jooq.enums.DecisionHistoryAction
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.DecisionHistory.Companion.DECISION_HISTORY
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
) {

    @Transactional(readOnly = true)
    fun listDecisions(meetingId: UUID): List<DecisionResponse> =
        dsl.selectFrom(DECISION)
            .where(DECISION.MEETING_ID.eq(meetingId))
            .orderBy(DECISION.CREATED_AT.asc())
            .fetch()
            .map { record ->
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
            }

    @Transactional
    fun confirmDecision(decisionId: UUID, request: ConfirmDecisionRequest): DecisionResponse {
        val actorId = localActorProvider.ensureHostUser()
        val now = OffsetDateTime.now()

        val record = dsl.selectFrom(DECISION)
            .where(DECISION.ID.eq(decisionId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "decision not found")

        val confirmedJson = JSON.valueOf(objectMapper.writeValueAsString(request.confirmedResult))
        val proposedJson = record.proposedResult ?: JSON.valueOf("{}")

        dsl.update(DECISION)
            .set(DECISION.STATUS, com.dudecide.app.jooq.enums.DecisionStatus.confirmed)
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

        return DecisionResponse(
            id = requireNotNull(record.id),
            meetingId = requireNotNull(record.meetingId),
            threadId = requireNotNull(record.threadId),
            decisionType = requireNotNull(record.decisionType).literal,
            title = requireNotNull(record.title),
            status = com.dudecide.shared.decision.DecisionStatus.CONFIRMED,
            proposedResult = jsonToMap(proposedJson),
            confirmedResult = jsonToMap(confirmedJson),
            decidedAt = now,
        )
    }

    private fun jsonToMap(value: JSON?): Map<String, Any?> {
        if (value == null || value.data().isBlank()) {
            return emptyMap()
        }

        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(value.data(), Map::class.java) as Map<String, Any?>
    }
}
