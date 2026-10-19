package com.dudecide.app.domain.chatimport.eval

import com.dudecide.app.domain.chatimport.FakeLlmClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths

class ChatImportEvaluationTest {

    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val cases = ChatImportEvalCases.load(Paths.get("src/test/resources/chat-import-eval/cases"), objectMapper)

    @Test
    fun `labeled cases load and every case scores with the fake provider`(@TempDir output: Path) {
        assertEquals(10, cases.size)
        val evaluation = ChatImportEvaluation(FakeLlmClient(), objectMapper)

        val report = evaluation.run(cases)
        val markdown = evaluation.write(report, output)

        assertEquals(emptyMap<String, String>(), report.failures)
        assertEquals(10, report.summary.cases)
        listOf(report.summary.decidedPrecision, report.summary.decidedRecall, report.summary.falseDecidedRate, report.summary.statusAccuracy)
            .forEach { assertTrue(it in 0.0..1.0) }
        assertTrue(markdown.toFile().readText().contains("False-decided rate"))
    }

    @Test
    fun `scorer rewards the gold answer and penalizes a false decision`() {
        val dateCase = cases.first { it.id == "01-date-confirmed" }
        val pseudonym = ChatImportEvaluation::pseudonym
        val gold = mapOf(
            "items" to listOf(
                mapOf("kind" to "decision", "decisionType" to "date", "status" to "likely_decided", "proposedResult" to mapOf("option" to "10월 18일")),
            ),
        )
        val wrong = mapOf(
            "items" to listOf(
                mapOf("kind" to "decision", "decisionType" to "location", "status" to "likely_decided", "proposedResult" to mapOf("option" to "강남")),
                mapOf("kind" to "open_question", "title" to "?"),
            ),
        )

        val good = ChatImportScorer.score(dateCase, gold, pseudonym) { null }
        val bad = ChatImportScorer.score(dateCase, wrong, pseudonym) { null }

        assertEquals(1, good.correctDecided)
        assertEquals(0, good.falseDecided)
        assertEquals(1, bad.falseDecided)
        assertEquals(1, bad.openQuestionError)
        assertEquals(1.0, ChatImportScorer.summarize(listOf(good)).decidedPrecision)
        assertEquals(1.0, ChatImportScorer.summarize(listOf(bad)).falseDecidedRate)
        assertTrue(ChatImportScorer.optionMatches("25th", "the 25th"))
        assertFalse(ChatImportScorer.optionMatches("18일", "11일"))
    }
}
