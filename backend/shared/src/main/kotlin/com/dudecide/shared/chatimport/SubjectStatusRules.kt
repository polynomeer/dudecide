package com.dudecide.shared.chatimport

import java.time.Duration
import java.time.Instant

/**
 * Deterministic status inference for a merged chat subject. The LLM labels events; these rules decide status.
 * When rules disagree, the result leans to CONTESTED rather than LIKELY_DECIDED.
 */
class SubjectStatusRules(
    private val abandonAfter: Duration = Duration.ofHours(72),
    private val unconfirmedConfidenceCap: Double = 0.6,
) {

    fun assess(subject: Subject, asOf: Instant): SubjectAssessment {
        require(subject.events.isNotEmpty()) { "subject must have at least one event" }

        val events = attributeOptions(subject.events.sortedBy { it.sentAt })
        val activeSpeakers = events.map { it.event.speaker }.toSet()
        val tallies = tallyOptions(events)

        val leader = tallies.filter { it.supporters.isNotEmpty() }
            .maxWithOrNull(compareBy<OptionTally> { it.supporters.size }.thenBy { it.lastSupportAt })
        val superseded = tallies
            .filter { leader != null && it !== leader && it.lastSupportAt != null && it.lastSupportAt < leader.firstEventAt }
            .map { it.option }
            .toSet()

        val status = when {
            isAbandoned(events, asOf) -> SubjectStatus.ABANDONED
            isContested(tallies, superseded, leader, events) -> SubjectStatus.CONTESTED
            leader != null && isDecided(leader, activeSpeakers.size, events) -> SubjectStatus.LIKELY_DECIDED
            else -> SubjectStatus.OPEN
        }

        return SubjectAssessment(
            subjectKey = subject.subjectKey,
            decisionType = subject.decisionType,
            title = subject.title,
            status = status,
            leadingOption = leader?.option,
            options = tallies.map {
                OptionAssessment(
                    option = it.option,
                    supporterCount = it.supporters.size,
                    objectorCount = it.objectors.size,
                    superseded = it.option in superseded,
                )
            },
            evidenceMessageIds = events.map { it.event.messageId }.distinct(),
            objectionMessageIds = events.filter { it.event.kind == SubjectEventKind.OPPOSITION }.map { it.event.messageId }.distinct(),
            confidence = confidence(status, leader, events),
        )
    }

    private fun attributeOptions(events: List<SubjectEvent>): List<AttributedEvent> {
        var current: String? = null
        return events.map { event ->
            val option = event.option ?: current
            if (event.option != null && event.kind in FOCUS_KINDS) current = event.option
            AttributedEvent(event, option)
        }
    }

    private fun tallyOptions(events: List<AttributedEvent>): List<OptionTally> =
        events.filter { it.option != null }
            .groupBy { it.option!! }
            .map { (option, optionEvents) ->
                val stances = optionEvents
                    .filter { it.event.kind != SubjectEventKind.CONDITION }
                    .groupBy { it.event.speaker }
                    .mapValues { (_, speakerEvents) -> speakerEvents.last().event.kind }
                val supporters = stances.filterValues { it in SUPPORT_KINDS }.keys
                OptionTally(
                    option = option,
                    events = optionEvents,
                    supporters = supporters,
                    objectors = stances.filterValues { it == SubjectEventKind.OPPOSITION }.keys,
                    firstEventAt = optionEvents.first().event.sentAt,
                    lastSupportAt = optionEvents.lastOrNull { it.event.kind in SUPPORT_KINDS }?.event?.sentAt,
                    hasConfirmation = optionEvents.any {
                        it.event.kind == SubjectEventKind.CONFIRMATION && it.event.speaker in supporters
                    },
                )
            }

    private fun isAbandoned(events: List<AttributedEvent>, asOf: Instant): Boolean {
        val onlyProposals = events.all { it.event.kind == SubjectEventKind.PROPOSAL }
        val singleSpeaker = events.map { it.event.speaker }.distinct().size == 1
        val stale = Duration.between(events.last().event.sentAt, asOf) >= abandonAfter
        return onlyProposals && singleSpeaker && stale
    }

    private fun isContested(
        tallies: List<OptionTally>,
        superseded: Set<String>,
        leader: OptionTally?,
        events: List<AttributedEvent>,
    ): Boolean {
        val competingOptions = tallies.count { it.option !in superseded && it.supporters.size >= 2 } >= 2
        return competingOptions || (leader != null && hasLateOpposition(leader, events))
    }

    private fun isDecided(leader: OptionTally, activeSpeakerCount: Int, events: List<AttributedEvent>): Boolean {
        val supporterCount = leader.supporters.size
        val agreed = leader.hasConfirmation || (supporterCount >= 2 && supporterCount * 2 >= activeSpeakerCount)
        val competingProposalAfter = events.any {
            it.event.kind == SubjectEventKind.PROPOSAL && it.option != null && it.option != leader.option &&
                it.event.sentAt > leader.supportAnchor
        }
        return agreed && !hasLateOpposition(leader, events) && !competingProposalAfter
    }

    private fun hasLateOpposition(leader: OptionTally, events: List<AttributedEvent>): Boolean =
        events.any {
            it.event.kind == SubjectEventKind.OPPOSITION && it.option == leader.option &&
                it.event.sentAt > leader.supportAnchor
        }

    private fun confidence(status: SubjectStatus, leader: OptionTally?, events: List<AttributedEvent>): Double {
        if (status != SubjectStatus.LIKELY_DECIDED || leader == null) {
            return events.minOf { it.event.confidence }
        }
        val deciding = leader.events.filter { it.event.kind in SUPPORT_KINDS }.minOf { it.event.confidence }
        return if (leader.hasConfirmation) deciding else minOf(deciding, unconfirmedConfidenceCap)
    }

    private data class AttributedEvent(val event: SubjectEvent, val option: String?)

    private class OptionTally(
        val option: String,
        val events: List<AttributedEvent>,
        val supporters: Set<String>,
        val objectors: Set<String>,
        val firstEventAt: Instant,
        val lastSupportAt: Instant?,
        val hasConfirmation: Boolean,
    ) {
        val supportAnchor: Instant get() = requireNotNull(lastSupportAt) { "option $option has no support" }
    }

    private companion object {
        val SUPPORT_KINDS = setOf(SubjectEventKind.PROPOSAL, SubjectEventKind.SUPPORT, SubjectEventKind.CONFIRMATION)
        val FOCUS_KINDS = SUPPORT_KINDS
    }
}
