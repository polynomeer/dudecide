package com.dudecide.app.domain

import com.dudecide.app.api.dto.ThreadSummaryView
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
import java.util.UUID

@Service
class ThreadService(
    private val dsl: DSLContext,
) {

    @Transactional(readOnly = true)
    fun listThreads(meetingId: UUID): List<ThreadSummaryView> {
        val exists = dsl.fetchExists(
            dsl.selectOne().from(MEETING).where(MEETING.ID.eq(meetingId)),
        )
        if (!exists) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "meeting not found")
        }

        return dsl.selectFrom(TOPIC_THREAD)
            .where(TOPIC_THREAD.MEETING_ID.eq(meetingId))
            .orderBy(TOPIC_THREAD.SORT_ORDER.asc())
            .fetch()
            .map { thread ->
                val threadId = requireNotNull(thread.id)
                ThreadSummaryView(
                    id = threadId,
                    category = requireNotNull(thread.category).literal,
                    title = requireNotNull(thread.title),
                    status = requireNotNull(thread.status).literal,
                    sortOrder = requireNotNull(thread.sortOrder),
                    lastActivityAt = thread.lastActivityAt,
                    messageCount = dsl.fetchCount(MESSAGE, MESSAGE.THREAD_ID.eq(threadId)),
                    decisionCount = dsl.fetchCount(DECISION, DECISION.THREAD_ID.eq(threadId)),
                    openVoteCount = dsl.fetchCount(VOTE, VOTE.THREAD_ID.eq(threadId).and(VOTE.STATUS.eq(VoteStatus.open))),
                )
            }
    }
}
