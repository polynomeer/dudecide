package com.dudecide.app.domain

import com.dudecide.app.jooq.enums.MeetingRole
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.Meeting.Companion.MEETING
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.Message.Companion.MESSAGE
import com.dudecide.app.jooq.tables.TaskItem.Companion.TASK_ITEM
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import com.dudecide.app.jooq.tables.Vote.Companion.VOTE
import org.jooq.DSLContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class MeetingAccessService(
    private val dsl: DSLContext,
    private val localActorProvider: LocalActorProvider,
) {

    @Transactional(readOnly = true)
    fun requireMeetingMember(meetingId: UUID): UUID =
        requireMeetingRole(
            meetingId = meetingId,
            allowedRoles = setOf(MeetingRole.host, MeetingRole.co_host, MeetingRole.participant, MeetingRole.guest),
        )

    @Transactional(readOnly = true)
    fun requireMeetingHostOrCoHost(meetingId: UUID): UUID =
        requireMeetingRole(meetingId, setOf(MeetingRole.host, MeetingRole.co_host))

    @Transactional(readOnly = true)
    fun requireThreadMember(threadId: UUID): UUID =
        requireScopedRole(
            recordLoader = {
                dsl.select(TOPIC_THREAD.MEETING_ID)
                    .from(TOPIC_THREAD)
                    .where(TOPIC_THREAD.ID.eq(threadId))
                    .fetchOne()
            },
            aggregateName = "thread",
            aggregateId = threadId,
            allowedRoles = setOf(MeetingRole.host, MeetingRole.co_host, MeetingRole.participant, MeetingRole.guest),
        )

    @Transactional(readOnly = true)
    fun requireThreadHostOrCoHost(threadId: UUID): UUID =
        requireScopedRole(
            recordLoader = {
                dsl.select(TOPIC_THREAD.MEETING_ID)
                    .from(TOPIC_THREAD)
                    .where(TOPIC_THREAD.ID.eq(threadId))
                    .fetchOne()
            },
            aggregateName = "thread",
            aggregateId = threadId,
            allowedRoles = setOf(MeetingRole.host, MeetingRole.co_host),
        )

    @Transactional(readOnly = true)
    fun requireDecisionMember(decisionId: UUID): UUID =
        requireScopedRole(
            recordLoader = {
                dsl.select(DECISION.MEETING_ID)
                    .from(DECISION)
                    .where(DECISION.ID.eq(decisionId))
                    .fetchOne()
            },
            aggregateName = "decision",
            aggregateId = decisionId,
            allowedRoles = setOf(MeetingRole.host, MeetingRole.co_host, MeetingRole.participant, MeetingRole.guest),
        )

    @Transactional(readOnly = true)
    fun requireDecisionHostOrCoHost(decisionId: UUID): UUID =
        requireScopedRole(
            recordLoader = {
                dsl.select(DECISION.MEETING_ID)
                    .from(DECISION)
                    .where(DECISION.ID.eq(decisionId))
                    .fetchOne()
            },
            aggregateName = "decision",
            aggregateId = decisionId,
            allowedRoles = setOf(MeetingRole.host, MeetingRole.co_host),
        )

    @Transactional(readOnly = true)
    fun requireVoteMember(voteId: UUID): UUID =
        requireScopedRole(
            recordLoader = {
                dsl.select(VOTE.MEETING_ID)
                    .from(VOTE)
                    .where(VOTE.ID.eq(voteId))
                    .fetchOne()
            },
            aggregateName = "vote",
            aggregateId = voteId,
            allowedRoles = setOf(MeetingRole.host, MeetingRole.co_host, MeetingRole.participant, MeetingRole.guest),
        )

    @Transactional(readOnly = true)
    fun requireVoteHostOrCoHost(voteId: UUID): UUID =
        requireScopedRole(
            recordLoader = {
                dsl.select(VOTE.MEETING_ID)
                    .from(VOTE)
                    .where(VOTE.ID.eq(voteId))
                    .fetchOne()
            },
            aggregateName = "vote",
            aggregateId = voteId,
            allowedRoles = setOf(MeetingRole.host, MeetingRole.co_host),
        )

    @Transactional(readOnly = true)
    fun requireTaskMember(taskId: UUID): UUID =
        requireScopedRole(
            recordLoader = {
                dsl.select(TASK_ITEM.MEETING_ID)
                    .from(TASK_ITEM)
                    .where(TASK_ITEM.ID.eq(taskId))
                    .fetchOne()
            },
            aggregateName = "task",
            aggregateId = taskId,
            allowedRoles = setOf(MeetingRole.host, MeetingRole.co_host, MeetingRole.participant, MeetingRole.guest),
        )

    @Transactional(readOnly = true)
    fun requireTaskHostOrCoHost(taskId: UUID): UUID =
        requireScopedRole(
            recordLoader = {
                dsl.select(TASK_ITEM.MEETING_ID)
                    .from(TASK_ITEM)
                    .where(TASK_ITEM.ID.eq(taskId))
                    .fetchOne()
            },
            aggregateName = "task",
            aggregateId = taskId,
            allowedRoles = setOf(MeetingRole.host, MeetingRole.co_host),
        )

    @Transactional(readOnly = true)
    fun requireMessageEditor(messageId: UUID): UUID {
        val actorId = localActorProvider.currentUserId()
        val record = dsl.select(MESSAGE.SENDER_USER_ID, TOPIC_THREAD.MEETING_ID, MEMBERSHIP.ROLE)
            .from(MESSAGE)
            .join(TOPIC_THREAD).on(TOPIC_THREAD.ID.eq(MESSAGE.THREAD_ID))
            .leftJoin(MEMBERSHIP).on(
                MEMBERSHIP.MEETING_ID.eq(TOPIC_THREAD.MEETING_ID)
                    .and(MEMBERSHIP.USER_ID.eq(actorId)),
            )
            .where(MESSAGE.ID.eq(messageId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "message not found")

        val role = record.get(MEMBERSHIP.ROLE)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "meeting membership is required")
        val senderUserId = requireNotNull(record.get(MESSAGE.SENDER_USER_ID))
        if (senderUserId == actorId || role == MeetingRole.host || role == MeetingRole.co_host) {
            return actorId
        }

        throw ResponseStatusException(HttpStatus.FORBIDDEN, "host or co-host role is required")
    }

    private fun requireMeetingRole(meetingId: UUID, allowedRoles: Set<MeetingRole>): UUID {
        val actorId = localActorProvider.currentUserId()
        ensureMeetingExists(meetingId)

        val membershipRole = dsl.select(MEMBERSHIP.ROLE)
            .from(MEMBERSHIP)
            .where(MEMBERSHIP.MEETING_ID.eq(meetingId))
            .and(MEMBERSHIP.USER_ID.eq(actorId))
            .fetchOne(MEMBERSHIP.ROLE)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "meeting membership is required")

        if (membershipRole !in allowedRoles) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "host or co-host role is required")
        }
        return actorId
    }

    private fun requireScopedRole(
        recordLoader: () -> org.jooq.Record1<UUID?>?,
        aggregateName: String,
        aggregateId: UUID,
        allowedRoles: Set<MeetingRole>,
    ): UUID {
        val meetingId = recordLoader()?.value1()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "$aggregateName not found")
        return requireMeetingRole(meetingId, allowedRoles)
    }

    private fun ensureMeetingExists(meetingId: UUID) {
        val exists = dsl.fetchExists(
            dsl.selectOne()
                .from(MEETING)
                .where(MEETING.ID.eq(meetingId)),
        )
        if (!exists) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "meeting not found")
        }
    }
}
