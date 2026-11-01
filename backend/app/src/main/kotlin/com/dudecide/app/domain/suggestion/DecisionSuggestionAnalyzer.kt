package com.dudecide.app.domain.suggestion

import com.dudecide.app.config.ChatImportAiProperties
import com.dudecide.app.config.DecisionSuggestionProperties
import com.dudecide.app.domain.EventStreamService
import com.dudecide.app.domain.NotificationService
import com.dudecide.app.domain.chatimport.ChatAnalysisPipeline
import com.dudecide.app.domain.chatimport.LlmClient
import com.dudecide.app.domain.chatimport.LoadedImport
import com.dudecide.app.domain.chatimport.LoadedMessage
import com.dudecide.app.jooq.enums.DecisionType
import com.dudecide.app.jooq.enums.MeetingRole
import com.dudecide.app.jooq.enums.NotificationType
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.DecisionSuggestion.Companion.DECISION_SUGGESTION
import com.dudecide.app.jooq.tables.DecisionSuggestionRun.Companion.DECISION_SUGGESTION_RUN
import com.dudecide.app.jooq.tables.Message.Companion.MESSAGE
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.jooq.DSLContext
import org.jooq.JSON
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Finds decision candidates in live topic conversations. Reuses the chat import pipeline on a window of recent
 * messages, rate-limited per docs/engineering/decision-suggestions.md. Nothing reaches the board without a host.
 */
@Component
class DecisionSuggestionAnalyzer(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    llmClient: LlmClient,
    aiProperties: ChatImportAiProperties,
    meterRegistry: MeterRegistry,
    private val properties: DecisionSuggestionProperties,
    private val notificationService: NotificationService,
    private val eventStreamService: EventStreamService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val pipeline = ChatAnalysisPipeline(llmClient, aiProperties.maxConcurrency, aiProperties.timeoutSeconds, meterRegistry)

    /** Claims one quiet thread with enough new messages, or returns null. Safe with several pollers. */
    fun claimNext(now: OffsetDateTime = OffsetDateTime.now()): UUID? {
        val quietBefore = now.minusMinutes(properties.quietMinutes)
        val candidates = dsl.select(TOPIC_THREAD.ID, TOPIC_THREAD.MEETING_ID, TOPIC_THREAD.SUGGESTIONS_ANALYZED_AT, TOPIC_THREAD.LAST_ACTIVITY_AT)
            .from(TOPIC_THREAD)
            .where(TOPIC_THREAD.READ_ONLY.isFalse)
            .and(TOPIC_THREAD.LAST_ACTIVITY_AT.le(quietBefore))
            .and(
                TOPIC_THREAD.SUGGESTIONS_ANALYZED_AT.isNull
                    .or(TOPIC_THREAD.LAST_ACTIVITY_AT.gt(TOPIC_THREAD.SUGGESTIONS_ANALYZED_AT)),
            )
            .orderBy(TOPIC_THREAD.LAST_ACTIVITY_AT.asc())
            .limit(CLAIM_CANDIDATES)
            .fetch()

        return candidates.firstNotNullOfOrNull { row ->
            val threadId = requireNotNull(row.get(TOPIC_THREAD.ID))
            val meetingId = requireNotNull(row.get(TOPIC_THREAD.MEETING_ID))
            val analyzedAt = row.get(TOPIC_THREAD.SUGGESTIONS_ANALYZED_AT)
            val lastActivityAt = requireNotNull(row.get(TOPIC_THREAD.LAST_ACTIVITY_AT))
            threadId.takeIf {
                newMessageCount(threadId, analyzedAt) >= properties.minNewMessages &&
                    pendingCount(threadId) < properties.maxPendingPerThread &&
                    runsToday(meetingId, now) < properties.dailyPerMeeting &&
                    claim(threadId, analyzedAt, lastActivityAt)
            }
        }
    }

    /** Analyzes a claimed thread and stores new pending suggestions. Returns how many were created. */
    fun analyze(threadId: UUID, asOf: Instant = Instant.now()): Int {
        val meetingId = dsl.select(TOPIC_THREAD.MEETING_ID).from(TOPIC_THREAD).where(TOPIC_THREAD.ID.eq(threadId))
            .fetchOne(TOPIC_THREAD.MEETING_ID) ?: return 0
        val loaded = loadWindow(threadId)
        val created = runCatching {
            val result = pipeline.run(loaded, asOf)
            storeSuggestions(meetingId, threadId, result)
        }.onFailure { error ->
            logger.warn("Decision suggestion analysis failed thread={}", threadId, error)
            recordRun(meetingId, threadId, loaded.messages.size, 0, error.message ?: error.javaClass.simpleName)
        }.getOrElse { return 0 }

        recordRun(meetingId, threadId, loaded.messages.size, created.size, null)
        if (created.isNotEmpty()) {
            eventStreamService.publish(
                meetingId = meetingId,
                eventType = "thread.updated",
                payload = mapOf("meetingId" to meetingId, "threadId" to threadId, "kind" to "suggestion.created"),
            )
            created.forEach { (suggestionId, title) ->
                notificationService.notifyMembers(
                    meetingId = meetingId,
                    type = NotificationType.decision_suggested,
                    payload = mapOf("threadId" to threadId, "suggestionId" to suggestionId, "title" to title),
                    roles = setOf(MeetingRole.host, MeetingRole.co_host),
                )
            }
        }
        return created.size
    }

    // Speakers become "Participant A, B, …" in order of appearance; names never reach the provider.
    private fun loadWindow(threadId: UUID): LoadedImport {
        val rows = dsl.select(MESSAGE.ID, MESSAGE.SENDER_USER_ID, MESSAGE.IMPORT_SPEAKER_ID, MESSAGE.BODY, MESSAGE.CREATED_AT)
            .from(MESSAGE)
            .where(MESSAGE.THREAD_ID.eq(threadId))
            .orderBy(MESSAGE.CREATED_AT.desc(), MESSAGE.ID.desc())
            .limit(properties.windowMessages)
            .fetch()
            .reversed()
        val pseudonyms = linkedMapOf<UUID, String>()
        val messages = rows.mapIndexed { index, row ->
            val speakerId = row.get(MESSAGE.SENDER_USER_ID) ?: requireNotNull(row.get(MESSAGE.IMPORT_SPEAKER_ID))
            val pseudonym = pseudonyms.getOrPut(speakerId) { pseudonymFor(pseudonyms.size) }
            LoadedMessage(
                shortId = "m${index + 1}",
                messageId = requireNotNull(row.get(MESSAGE.ID)),
                speakerId = speakerId,
                pseudonym = pseudonym,
                sentAt = requireNotNull(row.get(MESSAGE.CREATED_AT)).toInstant(),
                body = requireNotNull(row.get(MESSAGE.BODY)),
            )
        }
        return LoadedImport(messages)
    }

    private fun storeSuggestions(meetingId: UUID, threadId: UUID, result: Map<String, Any?>): List<Pair<UUID, String>> {
        @Suppress("UNCHECKED_CAST")
        val items = (result["items"] as? List<Map<String, Any?>>).orEmpty()
            .filter { it["kind"] == "decision" && it["status"] in SUGGESTED_STATUSES }
        val known = knownSubjects(threadId)
        return items.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val evidence = (item["evidence"] as? List<Map<String, Any?>>).orEmpty()
            val decisionType = DecisionType.entries.firstOrNull { it.literal == item["decisionType"] } ?: DecisionType.other
            val title = (item["title"] as? String).orEmpty().trim().take(TITLE_LENGTH)
            if (evidence.isEmpty() || title.isBlank() || subjectKey(decisionType, title) in known) {
                return@mapNotNull null
            }
            val likelyDecided = item["status"] == "likely_decided"
            @Suppress("UNCHECKED_CAST")
            val proposedResult = if (likelyDecided) {
                (item["proposedResult"] as? Map<String, Any?>).orEmpty()
            } else {
                mapOf("options" to item["options"])
            }
            val suggestionId = UUID.randomUUID()
            dsl.insertInto(DECISION_SUGGESTION)
                .set(DECISION_SUGGESTION.ID, suggestionId)
                .set(DECISION_SUGGESTION.MEETING_ID, meetingId)
                .set(DECISION_SUGGESTION.THREAD_ID, threadId)
                .set(DECISION_SUGGESTION.STATUS, "pending")
                .set(DECISION_SUGGESTION.ANALYSIS_STATUS, item["status"] as String)
                .set(DECISION_SUGGESTION.DECISION_TYPE, decisionType)
                .set(DECISION_SUGGESTION.TITLE, title)
                .set(DECISION_SUGGESTION.PROPOSED_RESULT, toJson(proposedResult))
                .set(DECISION_SUGGESTION.EVIDENCE, toJson(evidence.map { mapOf("messageId" to it["messageId"], "role" to it["role"]) }))
                .execute()
            suggestionId to title
        }
    }

    // A subject the thread already decided, or one a host already saw (accepted or dismissed), is not suggested again.
    private fun knownSubjects(threadId: UUID): Set<String> {
        val decisions = dsl.select(DECISION.DECISION_TYPE, DECISION.TITLE).from(DECISION)
            .where(DECISION.THREAD_ID.eq(threadId))
            .fetch { subjectKey(requireNotNull(it.value1()), requireNotNull(it.value2())) }
        val suggestions = dsl.select(DECISION_SUGGESTION.DECISION_TYPE, DECISION_SUGGESTION.TITLE).from(DECISION_SUGGESTION)
            .where(DECISION_SUGGESTION.THREAD_ID.eq(threadId))
            .fetch { subjectKey(requireNotNull(it.value1()), requireNotNull(it.value2())) }
        return (decisions + suggestions).toSet()
    }

    private fun subjectKey(type: DecisionType, title: String) = "${type.literal}:${title.trim().lowercase()}"

    private fun newMessageCount(threadId: UUID, since: OffsetDateTime?): Int =
        dsl.fetchCount(
            MESSAGE,
            MESSAGE.THREAD_ID.eq(threadId).and(if (since == null) DSL.trueCondition() else MESSAGE.CREATED_AT.gt(since)),
        )

    private fun pendingCount(threadId: UUID): Int =
        dsl.fetchCount(DECISION_SUGGESTION, DECISION_SUGGESTION.THREAD_ID.eq(threadId).and(DECISION_SUGGESTION.STATUS.eq("pending")))

    private fun runsToday(meetingId: UUID, now: OffsetDateTime): Int =
        dsl.fetchCount(
            DECISION_SUGGESTION_RUN,
            DECISION_SUGGESTION_RUN.MEETING_ID.eq(meetingId).and(DECISION_SUGGESTION_RUN.CREATED_AT.gt(now.minusHours(24))),
        )

    // The watermark is the last activity seen at claim time, so messages posted during the analysis count as new.
    private fun claim(threadId: UUID, previous: OffsetDateTime?, analyzedThrough: OffsetDateTime): Boolean =
        dsl.update(TOPIC_THREAD)
            .set(TOPIC_THREAD.SUGGESTIONS_ANALYZED_AT, analyzedThrough)
            .where(TOPIC_THREAD.ID.eq(threadId))
            .and(if (previous == null) TOPIC_THREAD.SUGGESTIONS_ANALYZED_AT.isNull else TOPIC_THREAD.SUGGESTIONS_ANALYZED_AT.eq(previous))
            .execute() == 1

    private fun recordRun(meetingId: UUID, threadId: UUID, messageCount: Int, suggestionCount: Int, failure: String?) {
        dsl.insertInto(DECISION_SUGGESTION_RUN)
            .set(DECISION_SUGGESTION_RUN.ID, UUID.randomUUID())
            .set(DECISION_SUGGESTION_RUN.MEETING_ID, meetingId)
            .set(DECISION_SUGGESTION_RUN.THREAD_ID, threadId)
            .set(DECISION_SUGGESTION_RUN.MESSAGE_COUNT, messageCount)
            .set(DECISION_SUGGESTION_RUN.SUGGESTION_COUNT, suggestionCount)
            .set(DECISION_SUGGESTION_RUN.FAILURE_REASON, failure?.take(FAILURE_LENGTH))
            .execute()
    }

    private fun toJson(value: Any): JSON = JSON.valueOf(objectMapper.writeValueAsString(value))

    private companion object {
        const val CLAIM_CANDIDATES = 10
        const val TITLE_LENGTH = 200
        const val FAILURE_LENGTH = 500
        val SUGGESTED_STATUSES = setOf("likely_decided", "contested")

        fun pseudonymFor(index: Int): String =
            "Participant " + if (index < 26) ('A' + index).toString() else "${'A' + index % 26}${index / 26}"
    }
}
