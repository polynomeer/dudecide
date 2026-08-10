package com.dudecide.app.domain

import com.dudecide.app.api.dto.MeetingReportResponse
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.Meeting.Companion.MEETING
import com.dudecide.app.jooq.tables.MeetingReport.Companion.MEETING_REPORT
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import com.dudecide.app.jooq.tables.Vote.Companion.VOTE
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
class ReportService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    private val outboxService: OutboxService,
) {

    @Transactional(readOnly = true)
    fun getLatestReport(meetingId: UUID): MeetingReportResponse {
        requireMeeting(meetingId)

        val report = dsl.selectFrom(MEETING_REPORT)
            .where(MEETING_REPORT.MEETING_ID.eq(meetingId))
            .orderBy(MEETING_REPORT.VERSION.desc())
            .limit(1)
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "meeting report not found")

        return toResponse(report)
    }

    @Transactional
    fun refreshReport(meetingId: UUID): MeetingReportResponse {
        val meeting = dsl.selectFrom(MEETING)
            .where(MEETING.ID.eq(meetingId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "meeting not found")

        val nextVersion = (dsl.selectCount()
            .from(MEETING_REPORT)
            .where(MEETING_REPORT.MEETING_ID.eq(meetingId))
            .fetchOne(0, Int::class.java) ?: 0) + 1
        val generatedAt = OffsetDateTime.now()
        val reportId = UUID.randomUUID()

        val snapshot = mapOf(
            "meeting" to mapOf(
                "id" to meetingId,
                "title" to requireNotNull(meeting.title),
                "meetingType" to requireNotNull(meeting.meetingType),
                "status" to requireNotNull(meeting.status).literal,
            ),
            "counts" to mapOf(
                "threads" to dsl.fetchCount(TOPIC_THREAD, TOPIC_THREAD.MEETING_ID.eq(meetingId)),
                "decisions" to dsl.fetchCount(DECISION, DECISION.MEETING_ID.eq(meetingId)),
                "openVotes" to dsl.fetchCount(VOTE, VOTE.MEETING_ID.eq(meetingId).and(VOTE.STATUS.eq(com.dudecide.app.jooq.enums.VoteStatus.open))),
                "confirmedDecisions" to dsl.fetchCount(DECISION, DECISION.MEETING_ID.eq(meetingId).and(DECISION.STATUS.eq(com.dudecide.app.jooq.enums.DecisionStatus.confirmed))),
            ),
            "decisions" to dsl.selectFrom(DECISION)
                .where(DECISION.MEETING_ID.eq(meetingId))
                .orderBy(DECISION.CREATED_AT.asc())
                .fetch()
                .map { decision ->
                    mapOf(
                        "id" to requireNotNull(decision.id),
                        "threadId" to requireNotNull(decision.threadId),
                        "title" to requireNotNull(decision.title),
                        "decisionType" to requireNotNull(decision.decisionType).literal,
                        "status" to requireNotNull(decision.status).literal,
                        "decidedAt" to decision.decidedAt?.toString(),
                    )
                },
        )

        dsl.insertInto(MEETING_REPORT)
            .set(MEETING_REPORT.ID, reportId)
            .set(MEETING_REPORT.MEETING_ID, meetingId)
            .set(MEETING_REPORT.VERSION, nextVersion)
            .set(MEETING_REPORT.SNAPSHOT, JSON.valueOf(objectMapper.writeValueAsString(snapshot)))
            .set(MEETING_REPORT.GENERATED_AT, generatedAt)
            .execute()

        outboxService.append(
            aggregateType = "meeting",
            aggregateId = meetingId,
            eventType = "ReportRefreshed",
            payload = mapOf(
                "reportId" to reportId,
                "meetingId" to meetingId,
                "version" to nextVersion,
                "generatedAt" to generatedAt.toString(),
            ),
        )

        return MeetingReportResponse(
            id = reportId,
            meetingId = meetingId,
            version = nextVersion,
            snapshot = snapshot,
            generatedAt = generatedAt,
        )
    }

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

    private fun toResponse(record: com.dudecide.app.jooq.tables.records.MeetingReportRecord): MeetingReportResponse =
        MeetingReportResponse(
            id = requireNotNull(record.id),
            meetingId = requireNotNull(record.meetingId),
            version = requireNotNull(record.version),
            snapshot = jsonToMap(record.snapshot),
            generatedAt = requireNotNull(record.generatedAt),
        )

    private fun jsonToMap(value: JSON?): Map<String, Any?> {
        if (value == null || value.data().isBlank()) {
            return emptyMap()
        }

        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(value.data(), Map::class.java) as Map<String, Any?>
    }
}
