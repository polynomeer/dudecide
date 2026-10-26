package com.dudecide.app.domain

import com.dudecide.app.api.dto.MarkNotificationsReadResponse
import com.dudecide.app.api.dto.NotificationView
import com.dudecide.app.jooq.enums.MeetingRole
import com.dudecide.app.jooq.enums.NotificationType
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.Notification.Companion.NOTIFICATION
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
class NotificationService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    private val localActorProvider: LocalActorProvider,
    private val meetingAccessService: MeetingAccessService,
    private val eventStreamService: EventStreamService,
) {

    @Transactional(readOnly = true)
    fun listNotifications(meetingId: UUID): List<NotificationView> {
        val recipientUserId = meetingAccessService.requireMeetingMember(meetingId)
        return dsl.selectFrom(NOTIFICATION)
            .where(NOTIFICATION.MEETING_ID.eq(meetingId))
            .and(NOTIFICATION.RECIPIENT_USER_ID.eq(recipientUserId))
            .orderBy(NOTIFICATION.CREATED_AT.desc())
            .fetch()
            .map { record ->
                NotificationView(
                    id = requireNotNull(record.id),
                    meetingId = requireNotNull(record.meetingId),
                    recipientUserId = requireNotNull(record.recipientUserId),
                    type = requireNotNull(record.type).literal,
                    payload = jsonToMap(record.payload),
                    sentAt = record.sentAt,
                    readAt = record.readAt,
                    createdAt = requireNotNull(record.createdAt),
                )
            }
    }

    /** Marks one of the current user's notifications as read. Already-read notifications keep their first read time. */
    @Transactional
    fun markRead(meetingId: UUID, notificationId: UUID): NotificationView {
        val recipientUserId = meetingAccessService.requireMeetingMember(meetingId)
        val updated = dsl.update(NOTIFICATION)
            .set(NOTIFICATION.READ_AT, OffsetDateTime.now())
            .where(NOTIFICATION.ID.eq(notificationId))
            .and(NOTIFICATION.MEETING_ID.eq(meetingId))
            .and(NOTIFICATION.RECIPIENT_USER_ID.eq(recipientUserId))
            .and(NOTIFICATION.READ_AT.isNull)
            .execute()
        if (updated == 0 && !dsl.fetchExists(
                NOTIFICATION,
                NOTIFICATION.ID.eq(notificationId)
                    .and(NOTIFICATION.MEETING_ID.eq(meetingId))
                    .and(NOTIFICATION.RECIPIENT_USER_ID.eq(recipientUserId)),
            )
        ) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "notification not found")
        }
        return listNotifications(meetingId).first { it.id == notificationId }
    }

    /** Marks every unread notification of the current user in this meeting as read. */
    @Transactional
    fun markAllRead(meetingId: UUID): MarkNotificationsReadResponse {
        val recipientUserId = meetingAccessService.requireMeetingMember(meetingId)
        val updated = dsl.update(NOTIFICATION)
            .set(NOTIFICATION.READ_AT, OffsetDateTime.now())
            .where(NOTIFICATION.MEETING_ID.eq(meetingId))
            .and(NOTIFICATION.RECIPIENT_USER_ID.eq(recipientUserId))
            .and(NOTIFICATION.READ_AT.isNull)
            .execute()
        return MarkNotificationsReadResponse(updatedCount = updated)
    }

    @Transactional
    fun createNotification(
        meetingId: UUID,
        type: NotificationType,
        payload: Map<String, Any?>,
        recipientUserId: UUID? = null,
    ) {
        // Resolved here, not as a default argument: Kotlin evaluates defaults against the Spring proxy, whose fields are null.
        val recipient = recipientUserId ?: localActorProvider.currentUserId()
        val notificationId = UUID.randomUUID()
        dsl.insertInto(NOTIFICATION)
            .set(NOTIFICATION.ID, notificationId)
            .set(NOTIFICATION.MEETING_ID, meetingId)
            .set(NOTIFICATION.RECIPIENT_USER_ID, recipient)
            .set(NOTIFICATION.TYPE, type)
            .set(NOTIFICATION.PAYLOAD, JSON.valueOf(objectMapper.writeValueAsString(payload)))
            .execute()

        eventStreamService.publish(
            meetingId = meetingId,
            eventType = "notification.created",
            payload = mapOf(
                "meetingId" to meetingId,
                "notificationId" to notificationId,
                "type" to type.literal,
            ),
        )
    }

    /**
     * Sends one notification to each member of the meeting, optionally only to the given roles,
     * and skips [excludeUserId] (usually the actor, who already knows what they did).
     */
    @Transactional
    fun notifyMembers(
        meetingId: UUID,
        type: NotificationType,
        payload: Map<String, Any?>,
        excludeUserId: UUID? = null,
        roles: Set<MeetingRole>? = null,
    ): Int {
        var condition = MEMBERSHIP.MEETING_ID.eq(meetingId)
        if (roles != null) {
            condition = condition.and(MEMBERSHIP.ROLE.`in`(roles))
        }
        if (excludeUserId != null) {
            condition = condition.and(MEMBERSHIP.USER_ID.ne(excludeUserId))
        }
        val recipients = dsl.select(MEMBERSHIP.USER_ID)
            .from(MEMBERSHIP)
            .where(condition)
            .fetch(MEMBERSHIP.USER_ID)
            .filterNotNull()
        recipients.forEach { recipient ->
            createNotification(meetingId, type, payload, recipientUserId = recipient)
        }
        return recipients.size
    }

    @Transactional(readOnly = true)
    fun pendingDispatch(limit: Int = 50) =
        dsl.selectFrom(NOTIFICATION)
            .where(NOTIFICATION.SENT_AT.isNull)
            .orderBy(NOTIFICATION.CREATED_AT.asc())
            .limit(limit)
            .fetch()

    @Transactional
    fun markSent(notificationId: UUID, sentAt: OffsetDateTime = OffsetDateTime.now()) {
        dsl.update(NOTIFICATION)
            .set(NOTIFICATION.SENT_AT, sentAt)
            .where(NOTIFICATION.ID.eq(notificationId))
            .execute()
    }

    private fun jsonToMap(value: JSON?): Map<String, Any?> {
        if (value == null || value.data().isBlank()) {
            return emptyMap()
        }
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(value.data(), Map::class.java) as Map<String, Any?>
    }
}
