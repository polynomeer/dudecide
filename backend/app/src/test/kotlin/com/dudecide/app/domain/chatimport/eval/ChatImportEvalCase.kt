package com.dudecide.app.domain.chatimport.eval

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText

/** One labeled chat. Gold labels are human judgment about what the group actually concluded. */
data class ChatImportEvalCase(
    val id: String,
    val description: String,
    val messages: List<EvalMessage>,
    val expected: EvalExpectation,
)

data class EvalMessage(
    val speaker: String,
    /** Minutes after 2026-10-01 20:00 KST. */
    val minute: Long,
    val text: String,
)

data class EvalExpectation(
    val decisions: List<ExpectedDecision>,
    val openQuestions: Int,
    /** Speaker letters expected to own a task. */
    val taskOwners: List<String>,
)

data class ExpectedDecision(
    val decisionType: String,
    /** likely_decided, contested, open, or abandoned. */
    val status: String,
    val option: String? = null,
)

object ChatImportEvalCases {
    fun load(directory: Path, objectMapper: ObjectMapper): List<ChatImportEvalCase> =
        Files.list(directory).use { paths ->
            paths.filter { it.extension == "json" }
                .sorted()
                .map { objectMapper.readValue(it.readText(), ChatImportEvalCase::class.java) }
                .toList()
        }
}
