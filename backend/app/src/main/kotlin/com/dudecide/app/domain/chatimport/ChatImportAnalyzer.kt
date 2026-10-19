package com.dudecide.app.domain.chatimport

import com.dudecide.app.config.ChatImportAiProperties
import com.dudecide.app.domain.EventStreamService
import com.dudecide.app.jooq.enums.ChatImportStatus
import com.dudecide.app.jooq.tables.ChatImport.Companion.CHAT_IMPORT
import com.dudecide.app.jooq.tables.ImportSpeaker.Companion.IMPORT_SPEAKER
import com.dudecide.app.jooq.tables.Message.Companion.MESSAGE
import com.dudecide.shared.chatimport.ChatPrefilter
import com.dudecide.shared.chatimport.EpisodeSegmenter
import com.dudecide.shared.chatimport.ImportedMessage
import com.dudecide.shared.chatimport.Subject
import com.dudecide.shared.chatimport.SubjectEvent
import com.dudecide.shared.chatimport.SubjectStatus
import com.dudecide.shared.chatimport.SubjectStatusRules
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.jooq.DSLContext
import org.jooq.JSON
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs the batch analysis for one claimed import: prefilter, segment, map (LLM), reduce (LLM), deterministic
 * status rules, then stores the staged result. Nothing is applied to the board here.
 */
@Component
class ChatImportAnalyzer(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    private val llmClient: LlmClient,
    private val eventStreamService: EventStreamService,
    private val properties: ChatImportAiProperties,
    private val meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val prefilter = ChatPrefilter()
    private val segmenter = EpisodeSegmenter()
    private val rules = SubjectStatusRules()

    /** Atomically moves the oldest submitted import to analyzing; safe with several pollers. */
    fun claimNext(): UUID? {
        val candidates = dsl.select(CHAT_IMPORT.ID)
            .from(CHAT_IMPORT)
            .where(CHAT_IMPORT.STATUS.eq(ChatImportStatus.submitted))
            .orderBy(CHAT_IMPORT.CREATED_AT.asc())
            .limit(CLAIM_CANDIDATES)
            .fetch(CHAT_IMPORT.ID)
            .filterNotNull()

        return candidates.firstOrNull { importId ->
            dsl.update(CHAT_IMPORT)
                .set(CHAT_IMPORT.STATUS, ChatImportStatus.analyzing)
                .set(CHAT_IMPORT.PROGRESS, toJson(mapOf("stage" to "claimed")))
                .set(CHAT_IMPORT.CLAIMED_AT, OffsetDateTime.now())
                .set(CHAT_IMPORT.ATTEMPTS, CHAT_IMPORT.ATTEMPTS.plus(1))
                .where(CHAT_IMPORT.ID.eq(importId))
                .and(CHAT_IMPORT.STATUS.eq(ChatImportStatus.submitted))
                .execute() == 1
        }
    }

    fun analyze(importId: UUID) {
        val record = dsl.selectFrom(CHAT_IMPORT).where(CHAT_IMPORT.ID.eq(importId)).fetchOne() ?: return
        val meetingId = requireNotNull(record.meetingId)
        // Final writes only land while the import is still analyzing, so a delete during analysis wins.
        try {
            val result = run(importId, meetingId, requireNotNull(record.rangeEnd).toInstant())
            val stored = dsl.update(CHAT_IMPORT)
                .set(CHAT_IMPORT.STATUS, ChatImportStatus.ready)
                .set(CHAT_IMPORT.RESULT, toJson(result))
                .set(CHAT_IMPORT.PROGRESS, toJson(mapOf("stage" to "done")))
                .set(CHAT_IMPORT.PROMPT_VERSION, LlmClient.PROMPT_VERSION)
                .set(CHAT_IMPORT.MODEL_VERSIONS, toJson(llmClient.modelVersions))
                .set(CHAT_IMPORT.COMPLETED_AT, OffsetDateTime.now())
                .where(CHAT_IMPORT.ID.eq(importId))
                .and(CHAT_IMPORT.STATUS.eq(ChatImportStatus.analyzing))
                .execute() == 1
            if (!stored) return
            @Suppress("UNCHECKED_CAST")
            val items = result["items"] as List<Map<String, Any?>>
            val itemCount = items.size
            meterRegistry.counter("dudecide.import.analysis", "outcome", "ready").increment()
            items.forEach { item ->
                meterRegistry.counter(
                    "dudecide.import.items",
                    "kind", item["kind"] as String,
                    "status", (item["status"] as? String) ?: "none",
                ).increment()
            }
            eventStreamService.publish(
                meetingId = meetingId,
                eventType = "import.completed",
                payload = mapOf("meetingId" to meetingId, "importId" to importId, "itemCount" to itemCount),
            )
        } catch (exception: Exception) {
            logger.warn("Chat import analysis failed importId={}", importId, exception)
            val reason = (exception.message ?: exception.javaClass.simpleName).take(200)
            val stored = dsl.update(CHAT_IMPORT)
                .set(CHAT_IMPORT.STATUS, ChatImportStatus.failed)
                .set(CHAT_IMPORT.FAILURE_REASON, reason)
                .set(CHAT_IMPORT.COMPLETED_AT, OffsetDateTime.now())
                .where(CHAT_IMPORT.ID.eq(importId))
                .and(CHAT_IMPORT.STATUS.eq(ChatImportStatus.analyzing))
                .execute() == 1
            if (!stored) return
            meterRegistry.counter("dudecide.import.analysis", "outcome", "failed").increment()
            eventStreamService.publish(
                meetingId = meetingId,
                eventType = "import.failed",
                payload = mapOf("meetingId" to meetingId, "importId" to importId, "reason" to reason),
            )
        }
    }

    private fun run(importId: UUID, meetingId: UUID, asOf: Instant): Map<String, Any?> {
        progress(importId, meetingId, mapOf("stage" to "prefilter"))
        val (loaded, prefiltered, episodes) = timed("prefilter") {
            val loaded = loadMessages(importId)
            val prefiltered = prefilter.filter(loaded.messages.map { ImportedMessage(it.shortId, it.pseudonym, it.sentAt, it.body) })
            Triple(loaded, prefiltered, segmenter.segment(prefiltered))
        }

        val mapResults = timed("map") { mapEpisodes(importId, meetingId, episodes.map { episode ->
            EpisodeMapRequest(
                episodeIndex = episode.index,
                messages = episode.messages.map { LlmMessage(it.id, it.speaker, it.sentAt, it.body) },
                filteredCount = episode.filteredCount,
            )
        }) }
        val succeeded = mapResults.filterNotNull()
        val failedEpisodes = mapResults.size - succeeded.size
        if (failedEpisodes * 5 > mapResults.size) {
            error("map stage failed for $failedEpisodes of ${mapResults.size} episodes")
        }

        progress(importId, meetingId, mapOf("stage" to "reduce"))
        val localSubjects = succeeded.flatMap { it.subjects }
        val merged = if (localSubjects.isEmpty()) {
            emptyList()
        } else {
            timed("reduce") { withRetry { llmClient.reduceSubjects(SubjectReduceRequest(localSubjects)) }.subjects }
        }

        progress(importId, meetingId, mapOf("stage" to "rules"))
        return timed("rules") { ChatImportResultAssembler(loaded).assemble(
            subjects = merged.mapNotNull { toSubject(it, loaded) },
            assess = { rules.assess(it, asOf) },
            questions = succeeded.flatMap { it.questions },
            tasks = succeeded.flatMap { it.tasks },
            stats = mapOf(
                "analyzedMessages" to prefiltered.kept.size,
                "filteredMessages" to prefiltered.dropped.size,
                "episodeCount" to episodes.size,
                "failedEpisodes" to failedEpisodes,
            ),
        ) }
    }

    private fun <T> timed(stage: String, block: () -> T): T {
        val started = System.nanoTime()
        try {
            return block()
        } finally {
            meterRegistry.timer("dudecide.import.stage", "stage", stage).record(Duration.ofNanos(System.nanoTime() - started))
        }
    }

    private fun mapEpisodes(importId: UUID, meetingId: UUID, requests: List<EpisodeMapRequest>): List<EpisodeMapResult?> {
        if (requests.isEmpty()) return emptyList()
        progress(importId, meetingId, mapOf("stage" to "map", "done" to 0, "total" to requests.size))

        val executor = Executors.newFixedThreadPool(properties.maxConcurrency.coerceIn(1, requests.size))
        try {
            val futures = requests.map { request ->
                executor.submit(Callable { withRetry { llmClient.mapEpisode(request) } })
            }
            return futures.mapIndexed { index, future ->
                val result = runCatching { future.get(properties.timeoutSeconds, TimeUnit.SECONDS) }
                    .onFailure { logger.warn("Episode map failed importId={} episode={}", importId, index, it) }
                    .getOrNull()
                progress(importId, meetingId, mapOf("stage" to "map", "done" to index + 1, "total" to requests.size))
                result?.let { sanitize(it, requests[index]) }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    // Drops anything that points at a message outside the episode; the LLM must not invent evidence.
    private fun sanitize(result: EpisodeMapResult, request: EpisodeMapRequest): EpisodeMapResult {
        val ids = request.messages.map { it.id }.toSet()
        val speakers = request.messages.map { it.speaker }.toSet()
        val dropped = result.subjects.sumOf { subject -> subject.events.count { it.messageId !in ids || it.speaker !in speakers } } +
            result.questions.count { it.messageId !in ids } +
            result.tasks.count { it.messageId !in ids }
        if (dropped > 0) {
            meterRegistry.counter("dudecide.import.llm.dropped", "stage", "map").increment(dropped.toDouble())
        }
        return EpisodeMapResult(
            messages = result.messages.filter { it.id in ids },
            subjects = result.subjects
                .map { subject -> subject.copy(events = subject.events.filter { it.messageId in ids && it.speaker in speakers }) }
                .filter { it.events.isNotEmpty() },
            questions = result.questions.filter { it.messageId in ids },
            tasks = result.tasks.filter { it.messageId in ids },
        )
    }

    private fun toSubject(merged: MergedSubject, loaded: LoadedImport): Subject? {
        val events = merged.events.mapNotNull { event ->
            val message = loaded.byShortId[event.messageId] ?: return@mapNotNull null
            SubjectEvent(event.messageId, message.pseudonym, event.kind, event.option, message.sentAt, event.confidence)
        }
        if (events.isEmpty()) return null
        return Subject(merged.subjectKey, merged.decisionType, merged.title, events)
    }

    private fun loadMessages(importId: UUID): LoadedImport {
        val messages = dsl.select(MESSAGE.ID, MESSAGE.BODY, MESSAGE.CREATED_AT, IMPORT_SPEAKER.ID, IMPORT_SPEAKER.PSEUDONYM)
            .from(MESSAGE)
            .join(IMPORT_SPEAKER).on(IMPORT_SPEAKER.ID.eq(MESSAGE.IMPORT_SPEAKER_ID))
            .where(MESSAGE.IMPORT_ID.eq(importId))
            .orderBy(MESSAGE.CREATED_AT.asc(), MESSAGE.ID.asc())
            .fetch()
            .mapIndexed { index, record ->
                LoadedMessage(
                    shortId = "m${index + 1}",
                    messageId = requireNotNull(record.get(MESSAGE.ID)),
                    speakerId = requireNotNull(record.get(IMPORT_SPEAKER.ID)),
                    pseudonym = requireNotNull(record.get(IMPORT_SPEAKER.PSEUDONYM)),
                    sentAt = requireNotNull(record.get(MESSAGE.CREATED_AT)).toInstant(),
                    body = requireNotNull(record.get(MESSAGE.BODY)),
                )
            }
        return LoadedImport(messages)
    }

    private fun progress(importId: UUID, meetingId: UUID, progress: Map<String, Any?>) {
        dsl.update(CHAT_IMPORT)
            .set(CHAT_IMPORT.PROGRESS, toJson(progress))
            .where(CHAT_IMPORT.ID.eq(importId))
            .execute()
        eventStreamService.publish(
            meetingId = meetingId,
            eventType = "import.progress",
            payload = mapOf(
                "meetingId" to meetingId,
                "importId" to importId,
                "status" to ChatImportStatus.analyzing.literal,
                "progress" to progress,
            ),
        )
    }

    private fun <T> withRetry(block: () -> T): T =
        runCatching(block).getOrElse { first ->
            logger.info("Retrying LLM call after failure: {}", first.message)
            block()
        }

    private fun toJson(value: Any): JSON = JSON.valueOf(objectMapper.writeValueAsString(value))

    private companion object {
        const val CLAIM_CANDIDATES = 5
    }
}

internal data class LoadedMessage(
    val shortId: String,
    val messageId: UUID,
    val speakerId: UUID,
    val pseudonym: String,
    val sentAt: Instant,
    val body: String,
)

internal class LoadedImport(val messages: List<LoadedMessage>) {
    val byShortId: Map<String, LoadedMessage> = messages.associateBy { it.shortId }
    val speakerIdByPseudonym: Map<String, UUID> = messages.associate { it.pseudonym to it.speakerId }
}

internal fun SubjectStatus.wireName(): String = name.lowercase()
