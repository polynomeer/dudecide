package com.dudecide.app.domain.chatimport

import com.dudecide.shared.chatimport.SubjectEventKind
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry

/** One structured-output call to a provider: the prompt goes in, schema-shaped JSON text comes out. */
interface JsonCompletionTransport {
    val provider: String

    fun complete(request: JsonCompletionRequest): JsonCompletion
}

data class JsonCompletionRequest(
    val stage: String,
    val model: String,
    val system: String,
    val user: String,
    val schemaName: String,
    val schema: Map<String, Any?>,
    val maxTokens: Long,
)

data class JsonCompletion(
    val json: String,
    val inputTokens: Long,
    val outputTokens: Long,
)

class LlmResponseException(message: String) : RuntimeException(message)

/**
 * Provider-neutral [LlmClient]: builds the versioned prompts, sends them through a [JsonCompletionTransport],
 * and validates the JSON before anything reaches the pipeline. Reduce only groups local subjects; events always
 * come from the map stage, so the model cannot invent evidence while merging.
 */
class PromptedLlmClient(
    private val transport: JsonCompletionTransport,
    private val mapModel: String,
    private val reduceModel: String,
    private val maxTokens: Long,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
) : LlmClient {

    override val modelVersions: Map<String, String> = mapOf(
        "provider" to transport.provider,
        "map" to mapModel,
        "reduce" to reduceModel,
    )

    override fun mapEpisode(request: EpisodeMapRequest): EpisodeMapResult {
        val input = mapOf(
            "episode" to request.episodeIndex,
            "skippedChatterCount" to request.filteredCount,
            "messages" to request.messages.map {
                mapOf("id" to it.id, "speaker" to it.speaker, "time" to it.sentAt.toString(), "text" to it.body)
            },
        )
        val root = call("map", mapModel, ChatImportPrompts.MAP_SYSTEM, input, "chat_episode_labels", ChatImportPrompts.MAP_SCHEMA)

        return EpisodeMapResult(
            messages = emptyList(),
            subjects = root.path("subjects").map { subject ->
                LocalSubject(
                    localKey = "${request.episodeIndex}-${subject.requireText("localKey")}",
                    decisionType = decisionType(subject.path("decisionType").asText()),
                    title = subject.requireText("title").take(TITLE_LENGTH),
                    events = subject.path("events").map { event ->
                        LocalSubjectEvent(
                            messageId = event.requireText("messageId"),
                            speaker = event.requireText("speaker"),
                            kind = eventKind(event.path("kind").asText()),
                            option = event.optionalText("option")?.take(OPTION_LENGTH),
                            confidence = event.path("confidence").asDouble(0.5).coerceIn(0.0, 1.0),
                        )
                    },
                )
            },
            questions = root.path("questions").map {
                ExtractedQuestion(messageId = it.requireText("messageId"), answeredBy = it.optionalText("answeredBy"))
            },
            tasks = root.path("tasks").map {
                ExtractedTask(
                    messageId = it.requireText("messageId"),
                    title = it.requireText("title").take(TITLE_LENGTH),
                    acceptedBy = it.optionalText("acceptedBy"),
                )
            },
        )
    }

    override fun reduceSubjects(request: SubjectReduceRequest): SubjectReduceResult {
        val byKey = request.subjects.associateBy { it.localKey }
        val input = mapOf(
            "subjects" to request.subjects.map { subject ->
                mapOf(
                    "localKey" to subject.localKey,
                    "decisionType" to subject.decisionType,
                    "title" to subject.title,
                    "options" to subject.events.mapNotNull { it.option }.distinct(),
                    "eventCount" to subject.events.size,
                )
            },
        )
        val root = call("reduce", reduceModel, ChatImportPrompts.REDUCE_SYSTEM, input, "chat_subject_groups", ChatImportPrompts.REDUCE_SCHEMA)

        val assigned = mutableSetOf<String>()
        val merged = root.path("subjects").mapNotNull { group ->
            val keys = group.path("mergedFrom").map { it.asText() }.filter { it in byKey && assigned.add(it) }
            if (keys.isEmpty()) return@mapNotNull null
            val aliases = group.path("optionAliases").associate { it.requireText("alias") to it.requireText("canonical") }
            MergedSubject(
                subjectKey = group.requireText("subjectKey"),
                decisionType = decisionType(group.path("decisionType").asText()),
                title = group.requireText("title").take(TITLE_LENGTH),
                events = keys.flatMap { key -> byKey.getValue(key).events }
                    .map { event -> event.copy(option = event.option?.let { aliases[it] ?: it }) },
                mergedFrom = keys,
            )
        }
        // A subject the model forgot to place stays on its own rather than disappearing.
        val leftovers = request.subjects.filter { it.localKey !in assigned }.map { subject ->
            MergedSubject(subject.localKey, subject.decisionType, subject.title, subject.events, listOf(subject.localKey))
        }
        return SubjectReduceResult(uniqueKeys(merged + leftovers))
    }

    private fun call(
        stage: String,
        model: String,
        system: String,
        input: Any,
        schemaName: String,
        schema: Map<String, Any?>,
    ): JsonNode {
        val completion = transport.complete(
            JsonCompletionRequest(
                stage = stage,
                model = model,
                system = system,
                user = objectMapper.writeValueAsString(input),
                schemaName = schemaName,
                schema = schema,
                maxTokens = maxTokens,
            ),
        )
        meterRegistry.counter("dudecide.import.llm.tokens", "provider", transport.provider, "stage", stage, "direction", "input")
            .increment(completion.inputTokens.toDouble())
        meterRegistry.counter("dudecide.import.llm.tokens", "provider", transport.provider, "stage", stage, "direction", "output")
            .increment(completion.outputTokens.toDouble())

        val root = runCatching { objectMapper.readTree(completion.json) }
            .getOrElse { throw LlmResponseException("$stage response is not JSON: ${it.message}") }
        if (!root.isObject) {
            throw LlmResponseException("$stage response is not a JSON object")
        }
        return root
    }

    private fun uniqueKeys(subjects: List<MergedSubject>): List<MergedSubject> {
        val seen = mutableMapOf<String, Int>()
        return subjects.map { subject ->
            val count = seen.merge(subject.subjectKey, 1, Int::plus)!!
            if (count == 1) subject else subject.copy(subjectKey = "${subject.subjectKey}-$count")
        }
    }

    private fun decisionType(value: String): String = value.takeIf { it in ChatImportPrompts.DECISION_TYPES } ?: "other"

    private fun eventKind(value: String): SubjectEventKind =
        SubjectEventKind.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw LlmResponseException("unknown event kind: $value")

    private fun JsonNode.requireText(field: String): String =
        path(field).takeIf { it.isTextual && it.asText().isNotBlank() }?.asText()
            ?: throw LlmResponseException("missing $field")

    private fun JsonNode.optionalText(field: String): String? =
        path(field).takeIf { it.isTextual && it.asText().isNotBlank() }?.asText()

    private companion object {
        const val TITLE_LENGTH = 200
        const val OPTION_LENGTH = 80
    }
}
