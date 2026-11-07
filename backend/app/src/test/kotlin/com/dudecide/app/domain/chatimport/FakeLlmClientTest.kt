package com.dudecide.app.domain.chatimport

import com.dudecide.shared.chatimport.SubjectEventKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class FakeLlmClientTest {

    private val client = FakeLlmClient()
    private val start = Instant.parse("2026-10-02T12:00:00Z")

    @Test
    fun `map extracts date events tasks and unanswered questions`() {
        val result = client.mapEpisode(
            episode(
                "Participant A" to "How about the 18th?",
                "Participant B" to "sounds good",
                "Participant C" to "No, that weekend is bad",
                "Participant B" to "I'll book the ferry",
                "Participant C" to "Who is driving?",
            ),
        )

        val date = result.subjects.single { it.decisionType == "date" }
        assertEquals(
            listOf(SubjectEventKind.PROPOSAL, SubjectEventKind.SUPPORT, SubjectEventKind.OPPOSITION),
            date.events.map { it.kind },
        )
        assertEquals("18th", date.events.first().option)
        assertEquals(listOf("m4"), result.tasks.map { it.messageId })
        assertEquals("Participant B", result.tasks.single().acceptedBy)
        assertEquals(listOf(ExtractedQuestion("m5", answeredBy = null)), result.questions)
        assertEquals(5, result.messages.size)
    }

    @Test
    fun `reduce merges subjects of the same decision type across episodes`() {
        val first = client.mapEpisode(episode("Participant A" to "18일 어때?"))
        val second = client.mapEpisode(episode("Participant B" to "18일로 확정", index = 1))

        val merged = client.reduceSubjects(SubjectReduceRequest(first.subjects + second.subjects)).subjects.single()

        assertEquals("date", merged.subjectKey)
        assertEquals(listOf("0-date", "1-date"), merged.mergedFrom)
        assertEquals(listOf(SubjectEventKind.PROPOSAL, SubjectEventKind.CONFIRMATION), merged.events.map { it.kind })
    }

    private fun episode(vararg messages: Pair<String, String>, index: Int = 0) =
        EpisodeMapRequest(
            episodeIndex = index,
            messages = messages.mapIndexed { i, (speaker, body) ->
                LlmMessage("m${i + 1}", speaker, start.plusSeconds(i * 60L), body)
            },
            filteredCount = 0,
        )
}
