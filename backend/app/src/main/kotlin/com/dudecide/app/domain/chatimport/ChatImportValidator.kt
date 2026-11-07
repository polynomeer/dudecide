package com.dudecide.app.domain.chatimport

import com.dudecide.app.api.dto.CreateChatImportRequest
import com.dudecide.app.jooq.enums.ChatImportSource
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

internal data class ValidatedSpeaker(
    val key: String,
    val displayName: String,
    val isSelf: Boolean,
    val membershipId: UUID?,
)

internal data class ValidatedMessage(
    val speakerKey: String,
    val sentAt: OffsetDateTime,
    val body: String,
)

internal data class ValidatedChatImport(
    val sourceType: ChatImportSource,
    val format: String,
    val roomName: String?,
    val rangeStart: OffsetDateTime,
    val rangeEnd: OffsetDateTime,
    val speakers: List<ValidatedSpeaker>,
    val messages: List<ValidatedMessage>,
    val stats: Map<String, Any?>,
    val consentVersion: String,
)

/** Server-side re-check of the client wizard limits. The client trims; the server rejects. */
internal object ChatImportValidator {

    const val MAX_MESSAGES = 3_000
    const val MAX_SPEAKERS = 200
    const val MAX_BODY_CHARS = 4_000
    val MAX_RANGE: Duration = Duration.ofDays(90)

    fun validate(request: CreateChatImportRequest): ValidatedChatImport {
        if (!request.consent.isParticipant || request.consent.version.isBlank()) {
            badRequest("consent from a chat participant is required")
        }

        val sourceType = ChatImportSource.entries.firstOrNull { it.literal == request.source.type }
            ?.takeIf { it.isSupportedForUpload() }
            ?: badRequest("unsupported source type: ${request.source.type}")
        val format = request.source.format.trim().takeIf { it.isNotEmpty() && it.length <= 64 }
            ?: badRequest("source format must be 1-64 characters")

        val rangeStart = request.range.start
        val rangeEnd = request.range.end
        if (rangeStart.isAfter(rangeEnd)) badRequest("range start must not be after range end")
        if (Duration.between(rangeStart, rangeEnd) > MAX_RANGE) badRequest("range exceeds limit of 90 days")

        if (request.messages.isEmpty()) badRequest("at least one message is required")
        if (request.messages.size > MAX_MESSAGES) badRequest("messages exceed limit of $MAX_MESSAGES")
        if (request.speakers.size > MAX_SPEAKERS) badRequest("speakers exceed limit of $MAX_SPEAKERS")

        val speakers = request.speakers.map {
            ValidatedSpeaker(
                key = it.key,
                displayName = it.displayName.trim().takeIf { name -> name.isNotEmpty() && name.length <= 120 }
                    ?: badRequest("speaker displayName must be 1-120 characters"),
                isSelf = it.isSelf == true,
                membershipId = it.membershipId,
            )
        }
        if (speakers.map { it.key }.toSet().size != speakers.size) badRequest("speaker keys must be unique")
        if (speakers.map { it.displayName }.toSet().size != speakers.size) badRequest("speaker display names must be unique")
        if (speakers.count { it.isSelf } > 1) badRequest("at most one speaker can be marked as self")

        val speakerKeys = speakers.map { it.key }.toSet()
        val messages = request.messages.map {
            if (it.speakerKey !in speakerKeys) badRequest("message references unknown speaker: ${it.speakerKey}")
            if (it.sentAt.isBefore(rangeStart) || it.sentAt.isAfter(rangeEnd)) badRequest("message sentAt is outside the range")
            if (it.body.isBlank()) badRequest("message body must not be blank")
            ValidatedMessage(it.speakerKey, it.sentAt, truncate(it.body))
        }.sortedBy { it.sentAt }

        val usedKeys = messages.map { it.speakerKey }.toSet()

        return ValidatedChatImport(
            sourceType = sourceType,
            format = format,
            roomName = request.source.roomName?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            speakers = speakers.filter { it.key in usedKeys },
            messages = messages,
            stats = mapOf(
                "formatVersion" to request.source.formatVersion,
                "parsedCount" to request.stats?.parsedCount,
                "uploadedCount" to request.stats?.uploadedCount,
                "maskedCount" to request.stats?.maskedCount,
                "unparsedLineCount" to request.stats?.unparsedLineCount,
                "mediaCounts" to request.mediaCounts.orEmpty().map { mapOf("date" to it.date.toString(), "count" to it.count) },
            ),
            consentVersion = request.consent.version.trim().take(64),
        )
    }

    private fun truncate(body: String): String {
        if (body.length <= MAX_BODY_CHARS) return body
        val cut = body.take(MAX_BODY_CHARS)
        return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }

    private fun badRequest(reason: String): Nothing =
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, reason)
}
