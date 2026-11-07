package com.dudecide.app.domain.chatimport.eval

import com.dudecide.app.config.ChatImportAiConfig
import com.dudecide.app.config.ChatImportAiProperties
import com.dudecide.app.domain.chatimport.ChatAnalysisPipeline
import com.dudecide.app.domain.chatimport.LlmClient
import com.dudecide.app.domain.chatimport.LoadedImport
import com.dudecide.app.domain.chatimport.LoadedMessage
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.io.path.writeText

/**
 * Offline evaluation of the chat import analysis over the labeled cases in `src/test/resources/chat-import-eval`.
 * Run with `gradle :backend:app:evalChatImport -PevalProvider=anthropic` (real providers spend API credits).
 */
class ChatImportEvaluation(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
) {
    data class Report(
        val provider: String,
        val promptVersion: String,
        val models: Map<String, String>,
        val generatedAt: String,
        val summary: ChatImportScorer.Summary,
        val cases: List<ChatImportScorer.CaseScore>,
        val failures: Map<String, String>,
    )

    fun run(cases: List<ChatImportEvalCase>): Report {
        val pipeline = ChatAnalysisPipeline(llmClient, maxConcurrency = 4, timeoutSeconds = 120, meterRegistry = SimpleMeterRegistry())
        val scores = mutableListOf<ChatImportScorer.CaseScore>()
        val failures = linkedMapOf<String, String>()

        cases.forEach { case ->
            val speakerIds = case.messages.map { it.speaker }.distinct().associateWith { uuid("${case.id}:speaker:$it") }
            val messages = case.messages.sortedBy { it.minute }.mapIndexed { index, message ->
                LoadedMessage(
                    shortId = "m${index + 1}",
                    messageId = uuid("${case.id}:message:$index"),
                    speakerId = speakerIds.getValue(message.speaker),
                    pseudonym = pseudonym(message.speaker),
                    sentAt = BASE_TIME.plusSeconds(message.minute * 60),
                    body = message.text,
                )
            }
            runCatching { pipeline.run(LoadedImport(messages), messages.last().sentAt) }
                .onSuccess { result ->
                    scores += ChatImportScorer.score(
                        case = case,
                        result = result,
                        pseudonymOf = ::pseudonym,
                        speakerIdOf = { name -> speakerIds.entries.firstOrNull { pseudonym(it.key) == name }?.value?.toString() },
                    )
                }
                .onFailure { failures[case.id] = it.message ?: it.javaClass.simpleName }
        }

        return Report(
            provider = llmClient.modelVersions["provider"] ?: "fake",
            promptVersion = LlmClient.PROMPT_VERSION,
            models = llmClient.modelVersions,
            generatedAt = OffsetDateTime.now().toString(),
            summary = ChatImportScorer.summarize(scores),
            cases = scores,
            failures = failures,
        )
    }

    fun write(report: Report, directory: Path): Path {
        Files.createDirectories(directory)
        val stem = "${report.promptVersion}-${report.provider}"
        directory.resolve("$stem.json").writeText(
            objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(report),
        )
        val markdown = directory.resolve("$stem.md")
        markdown.writeText(toMarkdown(report))
        return markdown
    }

    private fun toMarkdown(report: Report): String = buildString {
        val summary = report.summary
        appendLine("# Chat import evaluation: ${report.promptVersion} / ${report.provider}")
        appendLine()
        appendLine("Models: ${report.models}. Generated ${report.generatedAt}.")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("| --- | --- |")
        appendLine("| Cases scored | ${summary.cases} (${report.failures.size} failed) |")
        appendLine("| False-decided rate (lower is better) | ${percent(summary.falseDecidedRate)} |")
        appendLine("| Decided precision | ${percent(summary.decidedPrecision)} |")
        appendLine("| Decided recall | ${percent(summary.decidedRecall)} |")
        appendLine("| Status accuracy | ${percent(summary.statusAccuracy)} |")
        appendLine("| Task owner recall | ${percent(summary.taskRecall)} |")
        appendLine("| Open question mean error | ${"%.2f".format(summary.openQuestionMeanError)} |")
        appendLine()
        appendLine("| Case | Decided (correct/predicted/expected) | False decided | Status | Task owners | Question error |")
        appendLine("| --- | --- | --- | --- | --- | --- |")
        report.cases.forEach {
            appendLine(
                "| ${it.caseId} | ${it.correctDecided}/${it.predictedDecided}/${it.expectedDecided} | ${it.falseDecided} | " +
                    "${it.statusMatches}/${it.expectedDecisions} | ${it.taskOwnersFound}/${it.expectedTaskOwners} | ${it.openQuestionError} |",
            )
        }
        report.failures.forEach { (id, reason) -> appendLine("| $id | failed: $reason | | | | |") }
    }

    companion object {
        private val BASE_TIME: Instant = OffsetDateTime.parse("2026-10-01T20:00:00+09:00").toInstant()

        fun pseudonym(speaker: String): String = "Participant $speaker"

        private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

        private fun percent(value: Double): String = "%.0f%%".format(value * 100)
    }
}

/** Entry point for the `evalChatImport` Gradle task: `<provider> <cases dir> <output dir>`. */
fun main(args: Array<String>) {
    val provider = args.getOrElse(0) { "fake" }
    val casesDirectory = Paths.get(args.getOrElse(1) { "src/test/resources/chat-import-eval/cases" })
    val outputDirectory = Paths.get(args.getOrElse(2) { "build/reports/chat-import-eval" })
    val objectMapper = ObjectMapper().findAndRegisterModules()
    val properties = ChatImportAiProperties(
        provider = provider,
        mapModel = System.getenv("DUDECIDE_AI_MAP_MODEL").orEmpty(),
        reduceModel = System.getenv("DUDECIDE_AI_REDUCE_MODEL").orEmpty(),
    )
    val llmClient = ChatImportAiConfig().llmClient(properties, objectMapper, SimpleMeterRegistry())
    val evaluation = ChatImportEvaluation(llmClient, objectMapper)
    val report = evaluation.run(ChatImportEvalCases.load(casesDirectory, objectMapper))
    val markdown = evaluation.write(report, outputDirectory)
    println(markdown.toFile().readText())
    println("Report written to $markdown")
}
