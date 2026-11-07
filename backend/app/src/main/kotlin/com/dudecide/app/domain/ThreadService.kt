package com.dudecide.app.domain

import com.dudecide.app.api.dto.CreateThreadRequest
import com.dudecide.app.api.dto.ThreadSummaryView
import com.dudecide.app.jooq.enums.ThreadCategory
import com.dudecide.app.jooq.enums.VoteStatus
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.Meeting.Companion.MEETING
import com.dudecide.app.jooq.tables.Message.Companion.MESSAGE
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import com.dudecide.app.jooq.tables.Vote.Companion.VOTE
import org.jooq.DSLContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ThreadService(
    private val dsl: DSLContext,
    private val meetingAccessService: MeetingAccessService,
    private val outboxService: OutboxService,
    private val eventStreamService: EventStreamService,
) {

    @Transactional(readOnly = true)
    fun listThreads(meetingId: UUID): List<ThreadSummaryView> {
        meetingAccessService.requireMeetingMember(meetingId)

        return dsl.selectFrom(TOPIC_THREAD)
            .where(TOPIC_THREAD.MEETING_ID.eq(meetingId))
            .orderBy(TOPIC_THREAD.SORT_ORDER.asc())
            .fetch()
            .map { thread -> toSummary(thread) }
    }

    @Transactional
    fun createThread(meetingId: UUID, request: CreateThreadRequest): ThreadSummaryView {
        meetingAccessService.requireMeetingHostOrCoHost(meetingId)
        requireMeeting(meetingId)

        val threadId = UUID.randomUUID()
        val now = OffsetDateTime.now()
        val nextSortOrder = (dsl.select(org.jooq.impl.DSL.max(TOPIC_THREAD.SORT_ORDER))
            .from(TOPIC_THREAD)
            .where(TOPIC_THREAD.MEETING_ID.eq(meetingId))
            .fetchOne(0, Int::class.java) ?: -1) + 1

        dsl.insertInto(TOPIC_THREAD)
            .set(TOPIC_THREAD.ID, threadId)
            .set(TOPIC_THREAD.MEETING_ID, meetingId)
            .set(TOPIC_THREAD.CATEGORY, parseCategory(request.category))
            .set(TOPIC_THREAD.TITLE, request.title)
            .set(TOPIC_THREAD.SORT_ORDER, nextSortOrder)
            .set(TOPIC_THREAD.LAST_ACTIVITY_AT, now)
            .set(TOPIC_THREAD.CREATED_AT, now)
            .execute()

        outboxService.append(
            aggregateType = "meeting",
            aggregateId = meetingId,
            eventType = "ThreadCreated",
            payload = mapOf(
                "meetingId" to meetingId,
                "threadId" to threadId,
                "category" to request.category,
                "title" to request.title,
            ),
        )
        eventStreamService.publish(
            meetingId = meetingId,
            eventType = "thread.updated",
            payload = mapOf(
                "meetingId" to meetingId,
                "threadId" to threadId,
                "title" to request.title,
            ),
        )

        val thread = dsl.selectFrom(TOPIC_THREAD).where(TOPIC_THREAD.ID.eq(threadId)).fetchOne()
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "thread creation failed")
        return toSummary(thread)
    }

    private fun requireMeeting(meetingId: UUID) {
        val exists = dsl.fetchExists(
            dsl.selectOne().from(MEETING).where(MEETING.ID.eq(meetingId)),
        )
        if (!exists) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "meeting not found")
        }
    }

    private fun parseCategory(raw: String): ThreadCategory =
        runCatching { ThreadCategory.valueOf(raw.lowercase()) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid thread category: $raw") }

    private fun toSummary(thread: com.dudecide.app.jooq.tables.records.TopicThreadRecord): ThreadSummaryView {
        val threadId = requireNotNull(thread.id)
        return ThreadSummaryView(
            id = threadId,
            category = requireNotNull(thread.category).literal,
            title = requireNotNull(thread.title),
            status = requireNotNull(thread.status).literal,
            sortOrder = requireNotNull(thread.sortOrder),
            lastActivityAt = thread.lastActivityAt,
            messageCount = dsl.fetchCount(MESSAGE, MESSAGE.THREAD_ID.eq(threadId)),
            decisionCount = dsl.fetchCount(DECISION, DECISION.THREAD_ID.eq(threadId)),
            openVoteCount = dsl.fetchCount(VOTE, VOTE.THREAD_ID.eq(threadId).and(VOTE.STATUS.eq(VoteStatus.open))),
            readOnly = thread.readOnly == true,
        )
    }
}
