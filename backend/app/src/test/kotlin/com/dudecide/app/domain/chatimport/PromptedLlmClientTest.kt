package com.dudecide.app.domain.chatimport

import com.dudecide.shared.chatimport.SubjectEventKind
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PromptedLlmClientTest {

    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val registry = SimpleMeterRegistry()
    private val transport = ScriptedTransport()
    private val client = PromptedLlmClient(transport, "map-model", "reduce-model", 16_000, objectMapper, registry)

    @Test
    fun `map sends pseudonymous messages with the map schema and parses labels`() {
        transport.next = """
            {"subjects":[{"localKey":"d","decisionType":"date","title":"날짜",
              "events":[{"messageId":"m1","speaker":"Participant A","kind":"proposal","option":"18일","confidence":0.9},
                        {"messageId":"m2","speaker":"Participant B","kind":"support","option":null,"confidence":1.4}]}],
             "questions":[{"messageId":"m3","answeredBy":null}],
             "tasks":[{"messageId":"m2","title":"배편 예약","acceptedBy":"Participant B"}]}
        """.trimIndent()

        val result = client.mapEpisode(
            EpisodeMapRequest(
                episodeIndex = 2,
                messages = listOf(
                    LlmMessage("m1", "Participant A", Instant.parse("2026-10-01T12:00:00Z"), "18일 어때?"),
                    LlmMessage("m2", "Participant B", Instant.parse("2026-10-01T12:01:00Z"), "좋아 배편은 내가"),
                ),
                filteredCount = 1,
            ),
        )

        val request = transport.requests.single()
        assertEquals("map", request.stage)
        assertEquals("map-model", request.model)
        assertEquals(ChatImportPrompts.MAP_SCHEMA, request.schema)
        assertTrue(request.user.contains("Participant A"))
        assertFalse(request.user.contains("Minji"))

        val subject = result.subjects.single()
        assertEquals("2-d", subject.localKey)
        assertEquals(listOf(SubjectEventKind.PROPOSAL, SubjectEventKind.SUPPORT), subject.events.map { it.kind })
        assertEquals(1.0, subject.events[1].confidence)
        assertEquals(null, subject.events[1].option)
        assertEquals(listOf(ExtractedQuestion("m3", null)), result.questions)
        assertEquals("Participant B", result.tasks.single().acceptedBy)
        assertEquals(1.0, registry.counter("dudecide.import.llm.tokens", "provider", "scripted", "stage", "map", "direction", "input").count())
    }

    @Test
    fun `reduce groups local subjects, canonicalizes options, and keeps forgotten subjects`() {
        val first = LocalSubject("0-d", "date", "날짜", listOf(event("m1", "10/18", SubjectEventKind.PROPOSAL)))
        val second = LocalSubject("1-d", "date", "날짜", listOf(event("m5", "18일", SubjectEventKind.CONFIRMATION)))
        val lodging = LocalSubject("1-l", "lodging", "숙소", listOf(event("m6", "애월", SubjectEventKind.PROPOSAL)))
        transport.next = """
            {"subjects":[{"subjectKey":"date","decisionType":"date","title":"여행 날짜",
              "mergedFrom":["0-d","1-d","ghost"],"optionAliases":[{"alias":"10/18","canonical":"18일"}]}]}
        """.trimIndent()

        val merged = client.reduceSubjects(SubjectReduceRequest(listOf(first, second, lodging))).subjects

        assertEquals(listOf("date", "1-l"), merged.map { it.subjectKey })
        assertEquals(listOf("18일", "18일"), merged[0].events.map { it.option })
        assertEquals(listOf("0-d", "1-d"), merged[0].mergedFrom)
        assertFalse(transport.requests.single().user.contains("m1"), "reduce input should not carry message ids")
    }

    @Test
    fun `unknown decision types fall back to other and malformed responses fail`() {
        transport.next = """{"subjects":[{"localKey":"x","decisionType":"menu","title":"저녁","events":[]}],"questions":[],"tasks":[]}"""
        val result = client.mapEpisode(EpisodeMapRequest(0, emptyList(), 0))
        assertEquals("other", result.subjects.single().decisionType)

        transport.next = "not json"
        assertThrows(LlmResponseException::class.java) { client.mapEpisode(EpisodeMapRequest(0, emptyList(), 0)) }

        transport.next = """{"subjects":[{"localKey":"x","decisionType":"date","title":"t","events":[{"messageId":"m1","speaker":"A","kind":"maybe","option":null,"confidence":1}]}],"questions":[],"tasks":[]}"""
        assertThrows(LlmResponseException::class.java) { client.mapEpisode(EpisodeMapRequest(0, emptyList(), 0)) }
    }

    private fun event(messageId: String, option: String?, kind: SubjectEventKind) =
        LocalSubjectEvent(messageId, "Participant A", kind, option, 0.9)

    private class ScriptedTransport : JsonCompletionTransport {
        override val provider = "scripted"
        val requests = mutableListOf<JsonCompletionRequest>()
        var next: String = "{}"

        override fun complete(request: JsonCompletionRequest): JsonCompletion {
            requests += request
            return JsonCompletion(next, inputTokens = 1, outputTokens = 2)
        }
    }
}
