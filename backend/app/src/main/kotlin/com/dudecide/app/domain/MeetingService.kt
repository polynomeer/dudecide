package com.dudecide.app.domain

import com.dudecide.app.api.dto.CreateMeetingRequest
import com.dudecide.app.api.dto.CreateInviteLinkRequest
import com.dudecide.app.api.dto.InviteLinkResponse
import com.dudecide.app.api.dto.JoinInviteRequest
import com.dudecide.app.api.dto.MeetingDetailResponse
import com.dudecide.app.api.dto.MeetingResponse
import com.dudecide.app.api.dto.MeetingStatusDto
import com.dudecide.app.api.dto.MembershipResponse
import com.dudecide.app.api.dto.ThreadSummaryView
import com.dudecide.app.api.dto.UpdateMembershipRequest
import com.dudecide.app.jooq.enums.AttendanceStatus
import com.dudecide.app.jooq.enums.MeetingRole
import com.dudecide.app.jooq.enums.MeetingStatus
import com.dudecide.app.jooq.enums.VoteStatus
import com.dudecide.app.jooq.enums.ThreadCategory
import com.dudecide.app.jooq.tables.AppUser.Companion.APP_USER
import com.dudecide.app.jooq.tables.InviteLink.Companion.INVITE_LINK
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.Meeting.Companion.MEETING
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import com.dudecide.app.jooq.tables.Vote.Companion.VOTE
import org.jooq.DSLContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Service
class MeetingService(
    private val dsl: DSLContext,
    private val localActorProvider: LocalActorProvider,
    private val outboxService: OutboxService,
) {

    @Transactional
    fun createMeeting(request: CreateMeetingRequest): MeetingResponse {
        val ownerId = localActorProvider.ensureHostUser()
        val meetingId = UUID.randomUUID()

        dsl.insertInto(MEETING)
            .set(MEETING.ID, meetingId)
            .set(MEETING.OWNER_USER_ID, ownerId)
            .set(MEETING.TITLE, request.title)
            .set(MEETING.MEETING_TYPE, request.meetingType)
            .set(MEETING.STATUS, MeetingStatus.draft)
            .set(MEETING.ORIGIN_REGION, request.originRegion)
            .set(MEETING.EXPECTED_HEADCOUNT, request.expectedHeadcount)
            .set(MEETING.BUDGET_MIN, request.budget?.min?.toBigDecimal())
            .set(MEETING.BUDGET_MAX, request.budget?.max?.toBigDecimal())
            .execute()

        dsl.insertInto(MEMBERSHIP)
            .set(MEMBERSHIP.MEETING_ID, meetingId)
            .set(MEMBERSHIP.USER_ID, ownerId)
            .set(MEMBERSHIP.ROLE, MeetingRole.host)
            .execute()

        createDefaultThreads(meetingId)

        outboxService.append(
            aggregateType = "meeting",
            aggregateId = meetingId,
            eventType = "MeetingCreated",
            payload = mapOf(
                "meetingId" to meetingId,
                "ownerUserId" to ownerId,
                "title" to request.title,
                "meetingType" to request.meetingType,
            ),
        )

        return MeetingResponse(
            id = meetingId,
            title = request.title,
            meetingType = request.meetingType,
            status = MeetingStatusDto.DRAFT,
        )
    }

    @Transactional(readOnly = true)
    fun getMeeting(meetingId: UUID): MeetingDetailResponse {
        val record = requireMeeting(meetingId)

        val memberCount = dsl.fetchCount(MEMBERSHIP, MEMBERSHIP.MEETING_ID.eq(meetingId))
        val decisionCount = dsl.fetchCount(DECISION, DECISION.MEETING_ID.eq(meetingId))
        val openVoteCount = dsl.fetchCount(VOTE, VOTE.MEETING_ID.eq(meetingId).and(VOTE.STATUS.eq(VoteStatus.open)))
        val threads = dsl.selectFrom(TOPIC_THREAD)
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
                    messageCount = dsl.fetchCount(com.dudecide.app.jooq.tables.Message.Companion.MESSAGE, com.dudecide.app.jooq.tables.Message.Companion.MESSAGE.THREAD_ID.eq(threadId)),
                    decisionCount = dsl.fetchCount(DECISION, DECISION.THREAD_ID.eq(threadId)),
                    openVoteCount = dsl.fetchCount(VOTE, VOTE.THREAD_ID.eq(threadId).and(VOTE.STATUS.eq(VoteStatus.open))),
                )
            }

        return MeetingDetailResponse(
            id = requireNotNull(record.id),
            title = requireNotNull(record.title),
            meetingType = requireNotNull(record.meetingType),
            status = requireNotNull(record.status).toDto(),
            originRegion = record.originRegion,
            expectedHeadcount = record.expectedHeadcount,
            budget = if (record.budgetMin != null || record.budgetMax != null) {
                com.dudecide.app.api.dto.BudgetRangeDto(
                    min = record.budgetMin?.toDouble(),
                    max = record.budgetMax?.toDouble(),
                )
            } else null,
            ownerUserId = requireNotNull(record.ownerUserId),
            memberCount = memberCount,
            decisionCount = decisionCount,
            openVoteCount = openVoteCount,
            threads = threads,
        )
    }

    @Transactional
    fun createInviteLink(meetingId: UUID, request: CreateInviteLinkRequest): InviteLinkResponse {
        requireMeeting(meetingId)

        val inviteId = UUID.randomUUID()
        val token = UUID.randomUUID().toString()
        val createdAt = OffsetDateTime.now()

        dsl.insertInto(INVITE_LINK)
            .set(INVITE_LINK.ID, inviteId)
            .set(INVITE_LINK.MEETING_ID, meetingId)
            .set(INVITE_LINK.TOKEN, token)
            .set(INVITE_LINK.STATUS, "active")
            .set(INVITE_LINK.EXPIRES_AT, request.expiresAt)
            .set(INVITE_LINK.CREATED_AT, createdAt)
            .execute()

        outboxService.append(
            aggregateType = "meeting",
            aggregateId = meetingId,
            eventType = "InviteLinkCreated",
            payload = mapOf(
                "inviteLinkId" to inviteId,
                "meetingId" to meetingId,
                "token" to token,
                "expiresAt" to request.expiresAt?.toString(),
            ),
        )

        return InviteLinkResponse(
            id = inviteId,
            meetingId = meetingId,
            token = token,
            status = "active",
            expiresAt = request.expiresAt,
            createdAt = createdAt,
        )
    }

    @Transactional
    fun joinWithInvite(token: String, request: JoinInviteRequest): MembershipResponse {
        val invite = dsl.selectFrom(INVITE_LINK)
            .where(INVITE_LINK.TOKEN.eq(token))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "invite link not found")

        val expiresAt = invite.expiresAt
        if (invite.status != "active" || (expiresAt != null && expiresAt.isBefore(OffsetDateTime.now()))) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invite link is no longer valid")
        }

        val userId = UUID.randomUUID()
        val membershipId = UUID.randomUUID()
        val joinedAt = OffsetDateTime.now()
        val meetingId = requireNotNull(invite.meetingId)

        dsl.insertInto(APP_USER)
            .set(APP_USER.ID, userId)
            .set(APP_USER.DISPLAY_NAME, request.displayName)
            .set(APP_USER.STATUS, "active")
            .execute()

        dsl.insertInto(MEMBERSHIP)
            .set(MEMBERSHIP.ID, membershipId)
            .set(MEMBERSHIP.MEETING_ID, meetingId)
            .set(MEMBERSHIP.USER_ID, userId)
            .set(MEMBERSHIP.ROLE, MeetingRole.guest)
            .set(MEMBERSHIP.ATTENDANCE_STATUS, AttendanceStatus.accepted)
            .set(MEMBERSHIP.JOINED_AT, joinedAt)
            .execute()

        outboxService.append(
            aggregateType = "meeting",
            aggregateId = meetingId,
            eventType = "MemberJoinedMeeting",
            payload = mapOf(
                "inviteLinkId" to requireNotNull(invite.id),
                "meetingId" to meetingId,
                "membershipId" to membershipId,
                "userId" to userId,
            ),
        )

        return MembershipResponse(
            id = membershipId,
            meetingId = meetingId,
            userId = userId,
            displayName = request.displayName,
            role = MeetingRole.guest.literal,
            attendanceStatus = AttendanceStatus.accepted.literal,
            joinedAt = joinedAt,
        )
    }

    @Transactional
    fun updateMembership(meetingId: UUID, memberId: UUID, request: UpdateMembershipRequest): MembershipResponse {
        requireMeeting(meetingId)

        val membership = dsl.select(MEMBERSHIP.asterisk(), APP_USER.DISPLAY_NAME)
            .from(MEMBERSHIP)
            .join(APP_USER).on(APP_USER.ID.eq(MEMBERSHIP.USER_ID))
            .where(MEMBERSHIP.ID.eq(memberId))
            .and(MEMBERSHIP.MEETING_ID.eq(meetingId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "membership not found")

        val nextRole = request.role?.let(::parseMeetingRole) ?: requireNotNull(membership.get(MEMBERSHIP.ROLE))
        val nextAttendanceStatus = request.attendanceStatus?.let(::parseAttendanceStatus)
            ?: requireNotNull(membership.get(MEMBERSHIP.ATTENDANCE_STATUS))

        dsl.update(MEMBERSHIP)
            .set(MEMBERSHIP.ROLE, nextRole)
            .set(MEMBERSHIP.ATTENDANCE_STATUS, nextAttendanceStatus)
            .where(MEMBERSHIP.ID.eq(memberId))
            .execute()

        outboxService.append(
            aggregateType = "meeting",
            aggregateId = meetingId,
            eventType = "MembershipUpdated",
            payload = mapOf(
                "meetingId" to meetingId,
                "membershipId" to memberId,
                "role" to nextRole.literal,
                "attendanceStatus" to nextAttendanceStatus.literal,
            ),
        )

        return MembershipResponse(
            id = requireNotNull(membership.get(MEMBERSHIP.ID)),
            meetingId = requireNotNull(membership.get(MEMBERSHIP.MEETING_ID)),
            userId = requireNotNull(membership.get(MEMBERSHIP.USER_ID)),
            displayName = requireNotNull(membership.get(APP_USER.DISPLAY_NAME)),
            role = nextRole.literal,
            attendanceStatus = nextAttendanceStatus.literal,
            joinedAt = requireNotNull(membership.get(MEMBERSHIP.JOINED_AT)),
        )
    }

    private fun createDefaultThreads(meetingId: UUID) {
        val defaults = listOf(
            ThreadSeed(ThreadCategory.date, "Date"),
            ThreadSeed(ThreadCategory.location, "Location"),
            ThreadSeed(ThreadCategory.lodging, "Lodging"),
            ThreadSeed(ThreadCategory.transport, "Transport"),
            ThreadSeed(ThreadCategory.free_chat, "Free Chat"),
        )

        defaults.forEachIndexed { index, seed ->
            dsl.insertInto(TOPIC_THREAD)
                .set(TOPIC_THREAD.ID, UUID.randomUUID())
                .set(TOPIC_THREAD.MEETING_ID, meetingId)
                .set(TOPIC_THREAD.CATEGORY, seed.category)
                .set(TOPIC_THREAD.TITLE, seed.title)
                .set(TOPIC_THREAD.SORT_ORDER, index)
                .execute()
        }
    }

    private fun Double.toBigDecimal(): BigDecimal = BigDecimal.valueOf(this)

    private fun requireMeeting(meetingId: UUID) =
        dsl.selectFrom(MEETING)
            .where(MEETING.ID.eq(meetingId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "meeting not found")

    private fun parseMeetingRole(raw: String): MeetingRole =
        runCatching { MeetingRole.valueOf(raw.lowercase()) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid meeting role: $raw") }

    private fun parseAttendanceStatus(raw: String): AttendanceStatus =
        runCatching { AttendanceStatus.valueOf(raw.lowercase()) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid attendance status: $raw") }

    private fun MeetingStatus.toDto(): MeetingStatusDto =
        when (this) {
            MeetingStatus.draft -> MeetingStatusDto.DRAFT
            MeetingStatus.active -> MeetingStatusDto.ACTIVE
            MeetingStatus.finalizing -> MeetingStatusDto.FINALIZING
            MeetingStatus.completed -> MeetingStatusDto.COMPLETED
            MeetingStatus.archived -> MeetingStatusDto.ARCHIVED
        }

    private data class ThreadSeed(
        val category: ThreadCategory,
        val title: String,
    )
}
