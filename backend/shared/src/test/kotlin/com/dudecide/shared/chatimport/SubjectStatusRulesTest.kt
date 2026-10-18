package com.dudecide.shared.chatimport

import com.dudecide.shared.chatimport.SubjectEventKind.CONDITION
import com.dudecide.shared.chatimport.SubjectEventKind.CONFIRMATION
import com.dudecide.shared.chatimport.SubjectEventKind.OPPOSITION
import com.dudecide.shared.chatimport.SubjectEventKind.PROPOSAL
import com.dudecide.shared.chatimport.SubjectEventKind.SUPPORT
import com.dudecide.shared.chatimport.SubjectEventKind.WITHDRAWAL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SubjectStatusRulesTest {

    private val rules = SubjectStatusRules()
    private val start = Instant.parse("2026-10-02T12:00:00Z")
    private val asOf = start.plus(Duration.ofDays(1))

    @Test
    fun `explicit confirmation decides the leading option`() {
        val result = assess(
            event("A", PROPOSAL, "10-18"),
            event("B", SUPPORT, null),
            event("A", CONFIRMATION, null, confidence = 0.95),
            participants = listOf("C", "D", "E"),
        )

        assertEquals(SubjectStatus.LIKELY_DECIDED, result.status)
        assertEquals("10-18", result.leadingOption)
        assertEquals(0.9, result.confidence)
    }

    @Test
    fun `majority support without confirmation decides with capped confidence`() {
        val result = assess(
            event("A", PROPOSAL, "aewol"),
            event("B", SUPPORT, null),
            event("C", SUPPORT, null),
            event("D", CONDITION, null),
        )

        assertEquals(SubjectStatus.LIKELY_DECIDED, result.status)
        assertEquals(0.6, result.confidence)
    }

    @Test
    fun `support from less than half of active speakers stays open`() {
        val result = assess(
            event("A", PROPOSAL, "aewol"),
            event("B", SUPPORT, null),
            participants = listOf("C", "D", "E"),
        )

        assertEquals(SubjectStatus.OPEN, result.status)
    }

    @Test
    fun `opposition after the last support makes the subject contested`() {
        val result = assess(
            event("A", PROPOSAL, "aewol"),
            event("B", SUPPORT, null),
            event("A", CONFIRMATION, null),
            event("C", OPPOSITION, null),
        )

        assertEquals(SubjectStatus.CONTESTED, result.status)
        assertEquals(1, result.objectionMessageIds.size)
    }

    @Test
    fun `two live options with two supporters each are contested even if one could pass`() {
        val result = assess(
            event("A", PROPOSAL, "aewol"),
            event("B", SUPPORT, null),
            event("C", PROPOSAL, "seogwipo"),
            event("D", SUPPORT, null),
            event("E", SUPPORT, "aewol"),
            event("F", SUPPORT, "aewol"),
        )

        assertEquals(SubjectStatus.CONTESTED, result.status)
        assertEquals("aewol", result.leadingOption)
    }

    @Test
    fun `later option that wins marks the earlier option superseded`() {
        val result = assess(
            event("A", PROPOSAL, "10-11"),
            event("B", SUPPORT, null),
            event("C", PROPOSAL, "10-18"),
            event("A", SUPPORT, null),
            event("B", SUPPORT, null),
            event("D", SUPPORT, null),
        )

        assertEquals(SubjectStatus.LIKELY_DECIDED, result.status)
        assertEquals("10-18", result.leadingOption)
        val earlier = result.options.single { it.option == "10-11" }
        assertTrue(earlier.superseded)
        assertEquals(2, earlier.supporterCount)
    }

    @Test
    fun `competing proposal after agreement keeps the subject open`() {
        val result = assess(
            event("A", PROPOSAL, "aewol"),
            event("B", SUPPORT, null),
            event("C", PROPOSAL, "jungmun"),
        )

        assertEquals(SubjectStatus.OPEN, result.status)
    }

    @Test
    fun `withdrawn support no longer counts`() {
        val result = assess(
            event("A", PROPOSAL, "aewol"),
            event("B", SUPPORT, null),
            event("B", WITHDRAWAL, null),
        )

        assertEquals(SubjectStatus.OPEN, result.status)
        assertEquals(1, result.options.single().supporterCount)
    }

    @Test
    fun `lone stale proposal is abandoned but a fresh one is open`() {
        val subject = subject(event("A", PROPOSAL, "camping"))

        assertEquals(SubjectStatus.ABANDONED, rules.assess(subject, start.plus(Duration.ofHours(72))).status)
        assertEquals(SubjectStatus.OPEN, rules.assess(subject, start.plus(Duration.ofHours(71))).status)
    }

    @Test
    fun `evidence lists every event message once in time order`() {
        val result = assess(
            event("A", PROPOSAL, "aewol"),
            event("B", SUPPORT, null),
            event("C", SUPPORT, null),
        )

        assertEquals(listOf("e0", "e1", "e2"), result.evidenceMessageIds)
    }

    private var sequence = 0

    private fun event(speaker: String, kind: SubjectEventKind, option: String?, confidence: Double = 0.9): SubjectEvent {
        val index = sequence++
        return SubjectEvent("e$index", speaker, kind, option, start.plus(Duration.ofMinutes(index.toLong())), confidence)
    }

    private fun subject(vararg events: SubjectEvent) = Subject("s1", "location", "Lodging area", events.toList())

    private fun assess(vararg events: SubjectEvent, participants: List<String> = emptyList()): SubjectAssessment {
        val chatter = participants.map { event(it, CONDITION, null) }
        return rules.assess(subject(*(events.toList() + chatter).toTypedArray()), asOf)
    }
}
