package com.dudecide.shared.decision

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DecisionStateMachineTest {

    private val machine = DecisionStateMachine()

    @Test
    fun `host may create proposed decision`() {
        val result = machine.evaluate(
            DecisionTransition(
                from = null,
                to = DecisionStatus.PROPOSED,
                actor = DecisionActor.HOST,
            ),
        )

        assertTrue(result.allowed)
    }

    @Test
    fun `participant cannot confirm proposed decision`() {
        val result = machine.evaluate(
            DecisionTransition(
                from = DecisionStatus.PROPOSED,
                to = DecisionStatus.CONFIRMED,
                actor = DecisionActor.PARTICIPANT,
                hasEvidence = true,
            ),
        )

        assertFalse(result.allowed)
    }

    @Test
    fun `co-host may move discussion into voting with linked open vote`() {
        val result = machine.evaluate(
            DecisionTransition(
                from = DecisionStatus.IN_DISCUSSION,
                to = DecisionStatus.VOTING,
                actor = DecisionActor.CO_HOST,
                voteLinked = true,
                voteOpen = true,
            ),
        )

        assertTrue(result.allowed)
    }

    @Test
    fun `worker may not auto-confirm voting decision without host authorization`() {
        val result = machine.evaluate(
            DecisionTransition(
                from = DecisionStatus.VOTING,
                to = DecisionStatus.CONFIRMED,
                actor = DecisionActor.WORKER_SYSTEM,
                voteResolved = true,
                hostAuthorizedAutoConfirm = false,
            ),
        )

        assertFalse(result.allowed)
    }

    @Test
    fun `worker may mark confirmed decision as needs reconsideration when condition changed`() {
        val result = machine.evaluate(
            DecisionTransition(
                from = DecisionStatus.CONFIRMED,
                to = DecisionStatus.NEEDS_RECONSIDERATION,
                actor = DecisionActor.WORKER_SYSTEM,
                materialConditionChanged = true,
            ),
        )

        assertTrue(result.allowed)
    }

    @Test
    fun `confirmed decision cannot transition directly back to voting`() {
        val result = machine.evaluate(
            DecisionTransition(
                from = DecisionStatus.CONFIRMED,
                to = DecisionStatus.VOTING,
                actor = DecisionActor.HOST,
                voteLinked = true,
                voteOpen = true,
            ),
        )

        assertFalse(result.allowed)
    }
}
