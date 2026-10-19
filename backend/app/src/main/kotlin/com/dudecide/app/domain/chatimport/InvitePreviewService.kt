package com.dudecide.app.domain.chatimport

import com.dudecide.app.api.dto.InvitePreviewDecision
import com.dudecide.app.api.dto.InvitePreviewResponse
import com.dudecide.app.jooq.enums.ChatImportStatus
import com.dudecide.app.jooq.enums.DecisionStatus
import com.dudecide.app.jooq.enums.TaskStatus
import com.dudecide.app.jooq.tables.ChatImport.Companion.CHAT_IMPORT
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.InviteLink.Companion.INVITE_LINK
import com.dudecide.app.jooq.tables.Meeting.Companion.MEETING
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.TaskItem.Companion.TASK_ITEM
import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.DSLContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.UUID

/**
 * What a not-yet-member sees behind a shared invite link: titles, statuses, and counts only.
 * No quotes and no speaker names leave the meeting before joining (product decision on shared links).
 */
@Service
class InvitePreviewService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) {

    @Transactional(readOnly = true)
    fun preview(token: String): InvitePreviewResponse {
        val invite = dsl.selectFrom(INVITE_LINK).where(INVITE_LINK.TOKEN.eq(token)).fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "invite link not found")
        val expiresAt = invite.expiresAt
        if (invite.status != "active" || (expiresAt != null && expiresAt.isBefore(OffsetDateTime.now()))) {
            throw ResponseStatusException(HttpStatus.GONE, "invite link is no longer valid")
        }
        val meetingId = requireNotNull(invite.meetingId)
        val meeting = dsl.selectFrom(MEETING).where(MEETING.ID.eq(meetingId)).fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "meeting not found")

        val decisions = dsl.select(DECISION.TITLE, DECISION.DECISION_TYPE, DECISION.STATUS)
            .from(DECISION)
            .where(DECISION.MEETING_ID.eq(meetingId))
            .orderBy(DECISION.CREATED_AT.asc())
            .fetch { record ->
                InvitePreviewDecision(
                    title = requireNotNull(record.get(DECISION.TITLE)),
                    decisionType = requireNotNull(record.get(DECISION.DECISION_TYPE)).literal,
                    status = requireNotNull(record.get(DECISION.STATUS)).literal,
                )
            }

        return InvitePreviewResponse(
            meetingTitle = requireNotNull(meeting.title),
            meetingStatus = requireNotNull(meeting.status).literal,
            memberCount = dsl.fetchCount(MEMBERSHIP, MEMBERSHIP.MEETING_ID.eq(meetingId)),
            decisions = decisions,
            pendingDecisionCount = decisions.count { it.status != DecisionStatus.confirmed.literal },
            confirmedDecisionCount = decisions.count { it.status == DecisionStatus.confirmed.literal },
            openTaskCount = dsl.fetchCount(
                TASK_ITEM,
                TASK_ITEM.MEETING_ID.eq(meetingId).and(TASK_ITEM.STATUS.notIn(TaskStatus.done, TaskStatus.cancelled)),
            ),
            openQuestionCount = openQuestionCount(meetingId),
        )
    }

    private fun openQuestionCount(meetingId: UUID): Int =
        dsl.select(CHAT_IMPORT.RESULT)
            .from(CHAT_IMPORT)
            .where(CHAT_IMPORT.MEETING_ID.eq(meetingId))
            .and(CHAT_IMPORT.STATUS.`in`(ChatImportStatus.ready, ChatImportStatus.applied))
            .fetch(CHAT_IMPORT.RESULT)
            .sumOf { result ->
                val data = result?.data() ?: return@sumOf 0
                val items = objectMapper.readTree(data).path("items")
                items.count { it.path("kind").asText() == "open_question" }
            }
}
