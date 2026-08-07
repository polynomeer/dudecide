package com.dudecide.app.domain

import com.dudecide.app.api.dto.CreateMeetingRequest
import com.dudecide.app.api.dto.MeetingResponse
import com.dudecide.app.api.dto.MeetingStatusDto
import com.dudecide.app.jooq.enums.MeetingRole
import com.dudecide.app.jooq.enums.MeetingStatus
import com.dudecide.app.jooq.enums.ThreadCategory
import com.dudecide.app.jooq.tables.Meeting.Companion.MEETING
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class MeetingService(
    private val dsl: DSLContext,
    private val localActorProvider: LocalActorProvider,
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

        return MeetingResponse(
            id = meetingId,
            title = request.title,
            meetingType = request.meetingType,
            status = MeetingStatusDto.DRAFT,
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

    private data class ThreadSeed(
        val category: ThreadCategory,
        val title: String,
    )
}
