package com.dudecide.app.domain

import com.dudecide.app.api.dto.CreateTaskRequest
import com.dudecide.app.api.dto.TaskView
import com.dudecide.app.api.dto.UpdateTaskRequest
import com.dudecide.app.jooq.enums.TaskStatus
import com.dudecide.app.jooq.tables.Meeting.Companion.MEETING
import com.dudecide.app.jooq.tables.TaskItem.Companion.TASK_ITEM
import org.jooq.DSLContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class TaskService(
    private val dsl: DSLContext,
    private val outboxService: OutboxService,
    private val eventStreamService: EventStreamService,
) {

    @Transactional(readOnly = true)
    fun listTasks(meetingId: UUID): List<TaskView> {
        requireMeeting(meetingId)
        return dsl.selectFrom(TASK_ITEM)
            .where(TASK_ITEM.MEETING_ID.eq(meetingId))
            .orderBy(TASK_ITEM.CREATED_AT.asc())
            .fetch()
            .map(::toView)
    }

    @Transactional
    fun createTask(meetingId: UUID, request: CreateTaskRequest): TaskView {
        requireMeeting(meetingId)
        val taskId = UUID.randomUUID()

        dsl.insertInto(TASK_ITEM)
            .set(TASK_ITEM.ID, taskId)
            .set(TASK_ITEM.MEETING_ID, meetingId)
            .set(TASK_ITEM.OWNER_USER_ID, request.ownerUserId)
            .set(TASK_ITEM.SOURCE_MESSAGE_ID, request.sourceMessageId)
            .set(TASK_ITEM.TITLE, request.title)
            .set(TASK_ITEM.STATUS, TaskStatus.todo)
            .set(TASK_ITEM.DUE_AT, request.dueAt)
            .execute()

        outboxService.append(
            aggregateType = "meeting",
            aggregateId = meetingId,
            eventType = "TaskCreated",
            payload = mapOf(
                "meetingId" to meetingId,
                "taskId" to taskId,
                "title" to request.title,
            ),
        )
        eventStreamService.publish(
            meetingId = meetingId,
            eventType = "meeting.updated",
            payload = mapOf(
                "meetingId" to meetingId,
                "taskId" to taskId,
                "kind" to "task.created",
            ),
        )

        return toView(requireTask(taskId))
    }

    @Transactional
    fun updateTask(taskId: UUID, request: UpdateTaskRequest): TaskView {
        val current = requireTask(taskId)
        val nextStatus = request.status?.let(::parseStatus) ?: requireNotNull(current.status)

        dsl.update(TASK_ITEM)
            .set(TASK_ITEM.TITLE, request.title ?: requireNotNull(current.title))
            .set(TASK_ITEM.STATUS, nextStatus)
            .set(TASK_ITEM.OWNER_USER_ID, request.ownerUserId ?: current.ownerUserId)
            .set(TASK_ITEM.DUE_AT, request.dueAt ?: current.dueAt)
            .where(TASK_ITEM.ID.eq(taskId))
            .execute()

        outboxService.append(
            aggregateType = "meeting",
            aggregateId = requireNotNull(current.meetingId),
            eventType = "TaskUpdated",
            payload = mapOf(
                "meetingId" to requireNotNull(current.meetingId),
                "taskId" to taskId,
                "status" to nextStatus.literal,
            ),
        )
        eventStreamService.publish(
            meetingId = requireNotNull(current.meetingId),
            eventType = "meeting.updated",
            payload = mapOf(
                "meetingId" to requireNotNull(current.meetingId),
                "taskId" to taskId,
                "kind" to "task.updated",
            ),
        )

        return toView(requireTask(taskId))
    }

    private fun requireMeeting(meetingId: UUID) {
        val exists = dsl.fetchExists(
            dsl.selectOne().from(MEETING).where(MEETING.ID.eq(meetingId)),
        )
        if (!exists) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "meeting not found")
        }
    }

    private fun requireTask(taskId: UUID) =
        dsl.selectFrom(TASK_ITEM)
            .where(TASK_ITEM.ID.eq(taskId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "task not found")

    private fun parseStatus(raw: String): TaskStatus =
        runCatching { TaskStatus.valueOf(raw.lowercase()) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid task status: $raw") }

    private fun toView(record: com.dudecide.app.jooq.tables.records.TaskItemRecord) =
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
