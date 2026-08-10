package com.dudecide.app.domain

import com.dudecide.app.api.dto.SearchDecisionView
import com.dudecide.app.api.dto.SearchMessageView
import com.dudecide.app.api.dto.SearchResponse
import com.dudecide.app.api.dto.TaskView
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.Meeting.Companion.MEETING
import com.dudecide.app.jooq.tables.Message.Companion.MESSAGE
import com.dudecide.app.jooq.tables.TaskItem.Companion.TASK_ITEM
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class SearchService(
    private val dsl: DSLContext,
) {

    @Transactional(readOnly = true)
    fun search(meetingId: UUID, query: String): SearchResponse {
        requireMeeting(meetingId)
        val normalized = query.trim()
        if (normalized.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank")
        }
        val pattern = "%${normalized.lowercase()}%"

        val messages = dsl.select(MESSAGE.ID, MESSAGE.THREAD_ID, MESSAGE.BODY, MESSAGE.CREATED_AT)
            .from(MESSAGE)
            .join(TOPIC_THREAD).on(TOPIC_THREAD.ID.eq(MESSAGE.THREAD_ID))
            .where(TOPIC_THREAD.MEETING_ID.eq(meetingId))
            .and(DSL.lower(MESSAGE.BODY).like(pattern))
            .orderBy(MESSAGE.CREATED_AT.desc())
            .limit(10)
            .fetch { record ->
                SearchMessageView(
                    id = requireNotNull(record.get(MESSAGE.ID)),
                    threadId = requireNotNull(record.get(MESSAGE.THREAD_ID)),
                    body = requireNotNull(record.get(MESSAGE.BODY)),
                    createdAt = requireNotNull(record.get(MESSAGE.CREATED_AT)),
                )
            }

        val decisions = dsl.selectFrom(DECISION)
            .where(DECISION.MEETING_ID.eq(meetingId))
            .and(DSL.lower(DECISION.TITLE).like(pattern))
            .orderBy(DECISION.CREATED_AT.desc())
            .limit(10)
            .fetch { record ->
                SearchDecisionView(
                    id = requireNotNull(record.id),
                    threadId = requireNotNull(record.threadId),
                    title = requireNotNull(record.title),
                    status = requireNotNull(record.status).literal,
                )
            }

        val tasks = dsl.selectFrom(TASK_ITEM)
            .where(TASK_ITEM.MEETING_ID.eq(meetingId))
            .and(DSL.lower(TASK_ITEM.TITLE).like(pattern))
            .orderBy(TASK_ITEM.CREATED_AT.desc())
            .limit(10)
            .fetch { record ->
                TaskView(
                    id = requireNotNull(record.id),
                    meetingId = requireNotNull(record.meetingId),
                    ownerUserId = record.ownerUserId,
                    sourceMessageId = record.sourceMessageId,
                    title = requireNotNull(record.title),
                    status = requireNotNull(record.status).literal,
                    dueAt = record.dueAt,
                    createdAt = requireNotNull(record.createdAt),
                )
            }

        return SearchResponse(
            meetingId = meetingId,
            query = normalized,
            messages = messages,
            decisions = decisions,
            tasks = tasks,
        )
    }

    private fun requireMeeting(meetingId: UUID) {
        val exists = dsl.fetchExists(
            dsl.selectOne().from(MEETING).where(MEETING.ID.eq(meetingId)),
        )
        if (!exists) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "meeting not found")
        }
    }
}
