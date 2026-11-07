package com.dudecide.app.domain.chatimport

import com.dudecide.shared.chatimport.ChatPrefilter
import com.dudecide.shared.chatimport.EpisodeSegmenter
import com.dudecide.shared.chatimport.ImportedMessage
import com.dudecide.shared.chatimport.Subject
import com.dudecide.shared.chatimport.SubjectEvent
import com.dudecide.shared.chatimport.SubjectStatusRules
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The analysis stages without storage: prefilter, segment, map (LLM), reduce (LLM), deterministic status rules,
 * result assembly. Shared by the import worker and the offline evaluation harness.
 */
class ChatAnalysisPipeline(
    private val llmClient: LlmClient,
    private val maxConcurrency: Int,
    private val timeoutSeconds: Long,
    private val meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val prefilter = ChatPrefilter()
    private val segmenter = EpisodeSegmenter()
    private val rules = SubjectStatusRules()

    fun interface ProgressListener {
        fun onProgress(progress: Map<String, Any?>)
    }

    internal fun run(loaded: LoadedImport, asOf: Instant, listener: ProgressListener = ProgressListener {}): Map<String, Any?> {
        val (prefiltered, episodes) = timed("prefilter") {
            val prefiltered = prefilter.filter(loaded.messages.map { ImportedMessage(it.shortId, it.pseudonym, it.sentAt, it.body) })
            prefiltered to segmenter.segment(prefiltered)
        }

        val requests = episodes.map { episode ->
            EpisodeMapRequest(
                episodeIndex = episode.index,
                messages = episode.messages.map { LlmMessage(it.id, it.speaker, it.sentAt, it.body) },
                filteredCount = episode.filteredCount,
            )
        }
        val mapResults = timed("map") { mapEpisodes(requests, listener) }
        val succeeded = mapResults.filterNotNull()
        val failedEpisodes = mapResults.size - succeeded.size
        if (failedEpisodes * 5 > mapResults.size) {
            error("map stage failed for $failedEpisodes of ${mapResults.size} episodes")
        }

        listener.onProgress(mapOf("stage" to "reduce"))
        val localSubjects = succeeded.flatMap { it.subjects }
        val merged = if (localSubjects.isEmpty()) {
            emptyList()
        } else {
            timed("reduce") { withRetry { llmClient.reduceSubjects(SubjectReduceRequest(localSubjects)) }.subjects }
        }

        listener.onProgress(mapOf("stage" to "rules"))
        return timed("rules") {
            ChatImportResultAssembler(loaded).assemble(
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
            )
        }
    }

    private fun mapEpisodes(requests: List<EpisodeMapRequest>, listener: ProgressListener): List<EpisodeMapResult?> {
        if (requests.isEmpty()) return emptyList()
        listener.onProgress(mapOf("stage" to "map", "done" to 0, "total" to requests.size))

        val executor = Executors.newFixedThreadPool(maxConcurrency.coerceIn(1, requests.size))
        try {
            val futures = requests.map { request ->
                executor.submit(Callable { withRetry { llmClient.mapEpisode(request) } })
            }
            return futures.mapIndexed { index, future ->
                val result = runCatching { future.get(timeoutSeconds, TimeUnit.SECONDS) }
                    .onFailure { logger.warn("Episode map failed episode={}", index, it) }
                    .getOrNull()
                listener.onProgress(mapOf("stage" to "map", "done" to index + 1, "total" to requests.size))
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

    private fun <T> timed(stage: String, block: () -> T): T {
        val started = System.nanoTime()
        try {
            return block()
        } finally {
            meterRegistry.timer("dudecide.import.stage", "stage", stage).record(Duration.ofNanos(System.nanoTime() - started))
        }
    }

    private fun <T> withRetry(block: () -> T): T =
        runCatching(block).getOrElse { first ->
            logger.info("Retrying LLM call after failure: {}", first.message)
            block()
        }
}
