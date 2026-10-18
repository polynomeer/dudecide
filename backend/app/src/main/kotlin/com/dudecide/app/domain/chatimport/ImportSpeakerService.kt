package com.dudecide.app.domain.chatimport

import com.dudecide.app.api.dto.ClaimImportSpeakerRequest
import com.dudecide.app.api.dto.ClaimImportSpeakerResponse
import com.dudecide.app.api.dto.ImportSpeakerView
import com.dudecide.app.api.dto.UpdateImportSpeakerRequest
import com.dudecide.app.domain.EventStreamService
import com.dudecide.app.domain.MeetingAccessService
import com.dudecide.app.domain.OutboxService
import com.dudecide.app.jooq.enums.MeetingRole
import com.dudecide.app.jooq.tables.AppUser.Companion.APP_USER
import com.dudecide.app.jooq.tables.ChatImport.Companion.CHAT_IMPORT
import com.dudecide.app.jooq.tables.ImportSpeaker.Companion.IMPORT_SPEAKER
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import org.jooq.DSLContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.text.Normalizer
import java.util.UUID

/**
 * Speaker mapping keeps exported names away from ordinary members: only hosts, co-hosts, and the uploader
 * of a thread see them. Members claim themselves by typing the name they use in the messenger, so the list
 * of exported names is never enumerable.
 */
@Service
class ImportSpeakerService(
    private val dsl: DSLContext,
    private val meetingAccessService: MeetingAccessService,
    private val outboxService: OutboxService,
    private val eventStreamService: EventStreamService,
) {

    @Transactional(readOnly = true)
    fun listSpeakers(meetingId: UUID): List<ImportSpeakerView> {
        val actorId = meetingAccessService.requireMeetingMember(meetingId)
        val isHost = actorRole(meetingId, actorId) in HOST_ROLES
        val uploadedThreads = dsl.selectDistinct(CHAT_IMPORT.THREAD_ID)
            .from(CHAT_IMPORT)
            .where(CHAT_IMPORT.MEETING_ID.eq(meetingId))
            .and(CHAT_IMPORT.UPLOADED_BY_USER_ID.eq(actorId))
            .fetch(CHAT_IMPORT.THREAD_ID)
            .toSet()

        return dsl.select(
            IMPORT_SPEAKER.ID,
            IMPORT_SPEAKER.THREAD_ID,
            IMPORT_SPEAKER.DISPLAY_NAME,
            IMPORT_SPEAKER.PSEUDONYM,
            IMPORT_SPEAKER.MEMBERSHIP_ID,
            APP_USER.DISPLAY_NAME,
        )
            .from(IMPORT_SPEAKER)
            .leftJoin(MEMBERSHIP).on(MEMBERSHIP.ID.eq(IMPORT_SPEAKER.MEMBERSHIP_ID))
            .leftJoin(APP_USER).on(APP_USER.ID.eq(MEMBERSHIP.USER_ID))
            .where(IMPORT_SPEAKER.MEETING_ID.eq(meetingId))
            .orderBy(IMPORT_SPEAKER.THREAD_ID.asc(), IMPORT_SPEAKER.PSEUDONYM.asc())
            .fetch()
            .map { record ->
                val threadId = requireNotNull(record.get(IMPORT_SPEAKER.THREAD_ID))
                val privileged = isHost || threadId in uploadedThreads
                val pseudonym = requireNotNull(record.get(IMPORT_SPEAKER.PSEUDONYM))
                val memberName = record.get(APP_USER.DISPLAY_NAME)
                ImportSpeakerView(
                    id = requireNotNull(record.get(IMPORT_SPEAKER.ID)),
                    threadId = threadId,
                    label = memberName ?: pseudonym,
                    mapped = record.get(IMPORT_SPEAKER.MEMBERSHIP_ID) != null,
                    membershipId = record.get(IMPORT_SPEAKER.MEMBERSHIP_ID),
                    displayName = record.get(IMPORT_SPEAKER.DISPLAY_NAME)?.takeIf { privileged },
                    pseudonym = pseudonym.takeIf { privileged },
                )
            }
    }

    @Transactional
    fun updateSpeaker(speakerId: UUID, request: UpdateImportSpeakerRequest): ImportSpeakerView {
        val speaker = dsl.selectFrom(IMPORT_SPEAKER).where(IMPORT_SPEAKER.ID.eq(speakerId)).fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "speaker not found")
        val meetingId = requireNotNull(speaker.meetingId)
        meetingAccessService.requireMeetingHostOrCoHost(meetingId)

        request.membershipId?.let { membershipId ->
            val belongs = dsl.fetchExists(
                dsl.selectOne().from(MEMBERSHIP)
                    .where(MEMBERSHIP.ID.eq(membershipId))
                    .and(MEMBERSHIP.MEETING_ID.eq(meetingId)),
            )
            if (!belongs) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "membershipId does not belong to this meeting")
            }
        }
        dsl.update(IMPORT_SPEAKER)
            .set(IMPORT_SPEAKER.MEMBERSHIP_ID, request.membershipId)
            .where(IMPORT_SPEAKER.ID.eq(speakerId))
            .execute()
        announce(meetingId, listOf(speakerId), request.membershipId, requireNotNull(speaker.threadId))

        return listSpeakers(meetingId).single { it.id == speakerId }
    }

    @Transactional
    fun claimSpeaker(meetingId: UUID, request: ClaimImportSpeakerRequest): ClaimImportSpeakerResponse {
        val actorId = meetingAccessService.requireMeetingMember(meetingId)
        val membershipId = requireNotNull(
            dsl.select(MEMBERSHIP.ID).from(MEMBERSHIP)
                .where(MEMBERSHIP.MEETING_ID.eq(meetingId))
                .and(MEMBERSHIP.USER_ID.eq(actorId))
                .fetchOne(MEMBERSHIP.ID),
        )
        val name = normalize(request.displayName)
        if (name.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName must not be blank")
        }

        val speakers = dsl.selectFrom(IMPORT_SPEAKER).where(IMPORT_SPEAKER.MEETING_ID.eq(meetingId)).fetch()
        val alreadyClaimedOther = speakers.any {
            it.membershipId == membershipId && normalize(requireNotNull(it.displayName)) != name
        }
        if (alreadyClaimedOther) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "a different speaker is already claimed in this meeting")
        }

        val matches = speakers.filter { it.membershipId == null && normalize(requireNotNull(it.displayName)) == name }
        if (matches.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "no unclaimed speaker with that name")
        }
        val speakerIds = matches.map { requireNotNull(it.id) }
        dsl.update(IMPORT_SPEAKER)
            .set(IMPORT_SPEAKER.MEMBERSHIP_ID, membershipId)
            .where(IMPORT_SPEAKER.ID.`in`(speakerIds))
            .execute()
        matches.map { requireNotNull(it.threadId) }.distinct().forEach { threadId ->
            announce(meetingId, matches.filter { it.threadId == threadId }.map { requireNotNull(it.id) }, membershipId, threadId)
        }

        return ClaimImportSpeakerResponse(claimedSpeakerIds = speakerIds)
    }

    private fun announce(meetingId: UUID, speakerIds: List<UUID>, membershipId: UUID?, threadId: UUID) {
        outboxService.append(
            aggregateType = "import_speaker",
            aggregateId = speakerIds.first(),
            eventType = "ImportSpeakerMapped",
            payload = mapOf("meetingId" to meetingId, "speakerIds" to speakerIds, "membershipId" to membershipId),
        )
        eventStreamService.publish(
            meetingId = meetingId,
            eventType = "thread.updated",
            payload = mapOf("meetingId" to meetingId, "threadId" to threadId),
        )
    }

    private fun actorRole(meetingId: UUID, actorId: UUID): MeetingRole? =
        dsl.select(MEMBERSHIP.ROLE).from(MEMBERSHIP)
            .where(MEMBERSHIP.MEETING_ID.eq(meetingId))
            .and(MEMBERSHIP.USER_ID.eq(actorId))
            .fetchOne(MEMBERSHIP.ROLE)

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFC).trim().replace(WHITESPACE, " ").lowercase()

    private companion object {
        val HOST_ROLES = setOf(MeetingRole.host, MeetingRole.co_host)
        val WHITESPACE = Regex("\\s+")
    }
}
