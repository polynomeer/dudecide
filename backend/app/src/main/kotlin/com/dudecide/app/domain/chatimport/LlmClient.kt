package com.dudecide.app.domain.chatimport

import com.dudecide.shared.chatimport.SubjectEventKind
import java.time.Instant

/**
 * Port for the two LLM stages of chat import analysis. Inputs carry pseudonyms and short message ids only;
 * the LLM never sees real names or database ids, and never decides status (see SubjectStatusRules).
 */
interface LlmClient {
    val modelVersions: Map<String, String>

    fun mapEpisode(request: EpisodeMapRequest): EpisodeMapResult

    fun reduceSubjects(request: SubjectReduceRequest): SubjectReduceResult

    companion object {
        const val PROMPT_VERSION = "chat-import-v1"
    }
}

data class LlmMessage(
    val id: String,
    val speaker: String,
    val sentAt: Instant,
    val body: String,
)

data class EpisodeMapRequest(
    val episodeIndex: Int,
    val messages: List<LlmMessage>,
    val filteredCount: Int,
)

data class EpisodeMapResult(
    val messages: List<MessageLabel> = emptyList(),
    val subjects: List<LocalSubject> = emptyList(),
    val questions: List<ExtractedQuestion> = emptyList(),
    val tasks: List<ExtractedTask> = emptyList(),
)

data class MessageLabel(
    val id: String,
    val topics: List<String>,
    val intents: List<String>,
    val entities: Map<String, List<String>> = emptyMap(),
)

data class LocalSubject(
    val localKey: String,
    val decisionType: String,
    val title: String,
    val events: List<LocalSubjectEvent>,
)

data class LocalSubjectEvent(
    val messageId: String,
    val speaker: String,
    val kind: SubjectEventKind,
    val option: String?,
    val confidence: Double,
)

data class ExtractedQuestion(
    val messageId: String,
    val answeredBy: String?,
)

data class ExtractedTask(
    val messageId: String,
    val title: String,
    val acceptedBy: String?,
)

data class SubjectReduceRequest(
    val subjects: List<LocalSubject>,
)

data class SubjectReduceResult(
    val subjects: List<MergedSubject>,
)

data class MergedSubject(
    val subjectKey: String,
    val decisionType: String,
    val title: String,
    val events: List<LocalSubjectEvent>,
    val mergedFrom: List<String>,
)
