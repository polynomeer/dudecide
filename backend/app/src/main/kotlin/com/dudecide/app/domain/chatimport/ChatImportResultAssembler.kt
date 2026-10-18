package com.dudecide.app.domain.chatimport

import com.dudecide.shared.chatimport.Subject
import com.dudecide.shared.chatimport.SubjectAssessment
import com.dudecide.shared.chatimport.SubjectStatus

/** Builds the `chat-import-result-v1` document, translating short message ids back to database ids. */
internal class ChatImportResultAssembler(private val loaded: LoadedImport) {

    private var nextItem = 1

    fun assemble(
        subjects: List<Subject>,
        assess: (Subject) -> SubjectAssessment,
        questions: List<ExtractedQuestion>,
        tasks: List<ExtractedTask>,
        stats: Map<String, Any?>,
    ): Map<String, Any?> {
        val decisionItems = subjects.map { subject -> decisionItem(subject, assess(subject)) }
        val questionItems = questions.filter { it.answeredBy == null }.distinctBy { it.messageId }.mapNotNull(::questionItem)
        val taskItems = tasks.distinctBy { it.messageId }.mapNotNull(::taskItem)
        val assessments = decisionItems.map { it.second }

        return mapOf(
            "schemaVersion" to SCHEMA_VERSION,
            "items" to decisionItems.map { it.first } + questionItems + taskItems,
            "candidates" to mapOf(
                "dates" to candidateOptions(assessments, setOf("date")),
                "locations" to candidateOptions(assessments, setOf("location", "lodging")),
            ),
            "digest" to digest(assessments, questionItems.size, taskItems.size),
            "appliedItemIds" to emptyList<String>(),
            "stats" to stats,
        )
    }

    private fun decisionItem(subject: Subject, assessment: SubjectAssessment): Pair<Map<String, Any?>, SubjectAssessment> {
        val item = mapOf(
            "itemId" to nextItemId(),
            "kind" to "decision",
            "status" to assessment.status.wireName(),
            "applicable" to (assessment.status != SubjectStatus.ABANDONED),
            "decisionType" to assessment.decisionType,
            "title" to assessment.title,
            "proposedResult" to (
                assessment.leadingOption
                    ?.takeIf { assessment.status == SubjectStatus.LIKELY_DECIDED }
                    ?.let { mapOf("option" to it) }
                    ?: emptyMap<String, Any?>()
                ),
            "options" to assessment.options.map {
                mapOf(
                    "label" to it.option,
                    "supporters" to it.supporterCount,
                    "objectors" to it.objectorCount,
                    "superseded" to it.superseded,
                )
            },
            "evidence" to subject.events.sortedBy { it.sentAt }.mapNotNull { event ->
                messageId(event.messageId)?.let { mapOf("messageId" to it, "role" to event.kind.name.lowercase()) }
            }.distinct(),
            "objections" to assessment.objectionMessageIds.mapNotNull(::messageId),
            "confidence" to assessment.confidence,
        )
        return item to assessment
    }

    private fun questionItem(question: ExtractedQuestion): Map<String, Any?>? {
        val message = loaded.byShortId[question.messageId] ?: return null
        return mapOf(
            "itemId" to nextItemId(),
            "kind" to "open_question",
            "title" to message.body.take(TITLE_LENGTH),
            "evidence" to listOf(mapOf("messageId" to message.messageId.toString(), "role" to "question")),
        )
    }

    private fun taskItem(task: ExtractedTask): Map<String, Any?>? {
        val message = loaded.byShortId[task.messageId] ?: return null
        return mapOf(
            "itemId" to nextItemId(),
            "kind" to "task",
            "title" to task.title.take(TITLE_LENGTH),
            "ownerSpeakerId" to task.acceptedBy?.let { loaded.speakerIdByPseudonym[it]?.toString() },
            "evidence" to listOf(mapOf("messageId" to message.messageId.toString(), "role" to "task")),
        )
    }

    private fun candidateOptions(assessments: List<SubjectAssessment>, types: Set<String>): List<String> =
        assessments.filter { it.decisionType in types }
            .flatMap { assessment -> assessment.options.filterNot { it.superseded }.map { it.option } }
            .distinct()

    // Placeholder narrative until the real provider writes the digest.
    private fun digest(assessments: List<SubjectAssessment>, openQuestions: Int, tasks: Int): String {
        val counts = assessments.groupingBy { it.status }.eachCount()
        return listOf(
            "${counts[SubjectStatus.LIKELY_DECIDED] ?: 0} likely decided",
            "${counts[SubjectStatus.CONTESTED] ?: 0} contested",
            "${counts[SubjectStatus.OPEN] ?: 0} still open",
            "$openQuestions open questions",
            "$tasks tasks",
        ).joinToString(", ") + "."
    }

    private fun messageId(shortId: String): String? = loaded.byShortId[shortId]?.messageId?.toString()

    private fun nextItemId(): String = "it_${nextItem++}"

    private companion object {
        const val SCHEMA_VERSION = "chat-import-result-v1"
        const val TITLE_LENGTH = 200
    }
}
