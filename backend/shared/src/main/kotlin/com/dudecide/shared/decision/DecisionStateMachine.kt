package com.dudecide.shared.decision

class DecisionStateMachine {

    fun evaluate(transition: DecisionTransition): DecisionTransitionResult {
        if (transition.from == null) {
            return when {
                transition.to != DecisionStatus.PROPOSED ->
                    denied("new decisions must start in PROPOSED")
                transition.actor !in setOf(DecisionActor.HOST, DecisionActor.CO_HOST, DecisionActor.WORKER_SYSTEM) ->
                    denied("actor cannot create decision drafts")
                else -> allowed()
            }
        }

        return when (transition.from) {
            DecisionStatus.PROPOSED -> fromProposed(transition)
            DecisionStatus.IN_DISCUSSION -> fromInDiscussion(transition)
            DecisionStatus.VOTING -> fromVoting(transition)
            DecisionStatus.CONFIRMED -> fromConfirmed(transition)
            DecisionStatus.ON_HOLD -> fromOnHold(transition)
            DecisionStatus.NEEDS_RECONSIDERATION -> fromNeedsReconsideration(transition)
        }
    }

    private fun fromProposed(transition: DecisionTransition): DecisionTransitionResult = when (transition.to) {
        DecisionStatus.PROPOSED -> allowed()
        DecisionStatus.IN_DISCUSSION ->
            if (transition.actor in setOf(DecisionActor.HOST, DecisionActor.CO_HOST, DecisionActor.WORKER_SYSTEM)) allowed()
            else denied("actor cannot open discussion")
        DecisionStatus.VOTING ->
            if (transition.actor in setOf(DecisionActor.HOST, DecisionActor.CO_HOST) && transition.voteLinked) allowed()
            else denied("moving to VOTING requires host/co-host and linked vote")
        DecisionStatus.CONFIRMED ->
            if (transition.actor == DecisionActor.HOST && transition.hasEvidence) allowed()
            else denied("only host may confirm a proposed decision with evidence")
        DecisionStatus.ON_HOLD ->
            if (transition.actor in setOf(DecisionActor.HOST, DecisionActor.CO_HOST) && transition.blockingDependencyRecorded) allowed()
            else denied("moving to ON_HOLD requires host/co-host and a blocking dependency reason")
        DecisionStatus.NEEDS_RECONSIDERATION ->
            denied("a proposed decision cannot require reconsideration")
    }

    private fun fromInDiscussion(transition: DecisionTransition): DecisionTransitionResult = when (transition.to) {
        DecisionStatus.PROPOSED ->
            if (transition.actor in setOf(DecisionActor.HOST, DecisionActor.CO_HOST)) allowed()
            else denied("only host/co-host may demote a decision to PROPOSED")
        DecisionStatus.IN_DISCUSSION -> allowed()
        DecisionStatus.VOTING ->
            if (transition.actor in setOf(DecisionActor.HOST, DecisionActor.CO_HOST) && transition.voteLinked && transition.voteOpen) allowed()
            else denied("moving to VOTING requires host/co-host and an open linked vote")
        DecisionStatus.CONFIRMED ->
            if (transition.actor == DecisionActor.HOST && transition.hasEvidence) allowed()
            else denied("only host may confirm an in-discussion decision with evidence")
        DecisionStatus.ON_HOLD ->
            if (transition.actor in setOf(DecisionActor.HOST, DecisionActor.CO_HOST) && transition.blockingDependencyRecorded) allowed()
            else denied("moving to ON_HOLD requires host/co-host and a blocking dependency reason")
        DecisionStatus.NEEDS_RECONSIDERATION ->
            denied("an in-discussion decision cannot jump to reconsideration")
    }

    private fun fromVoting(transition: DecisionTransition): DecisionTransitionResult = when (transition.to) {
        DecisionStatus.PROPOSED -> denied("a voting decision cannot return directly to PROPOSED")
        DecisionStatus.IN_DISCUSSION ->
            if (transition.actor in setOf(DecisionActor.HOST, DecisionActor.CO_HOST)) allowed()
            else denied("only host/co-host may reopen a vote into discussion")
        DecisionStatus.VOTING -> allowed()
        DecisionStatus.CONFIRMED -> when {
            transition.actor == DecisionActor.HOST && transition.voteResolved -> allowed()
            transition.actor == DecisionActor.WORKER_SYSTEM && transition.voteResolved && transition.hostAuthorizedAutoConfirm -> allowed()
            else -> denied("confirming a voting decision requires a resolved vote and host-approved path")
        }
        DecisionStatus.ON_HOLD ->
            if (transition.actor in setOf(DecisionActor.HOST, DecisionActor.CO_HOST) && transition.blockingDependencyRecorded) allowed()
            else denied("moving to ON_HOLD requires host/co-host and a blocking dependency reason")
        DecisionStatus.NEEDS_RECONSIDERATION ->
            denied("a voting decision should not jump directly to reconsideration")
    }

    private fun fromConfirmed(transition: DecisionTransition): DecisionTransitionResult = when (transition.to) {
        DecisionStatus.PROPOSED -> denied("a confirmed decision cannot return to PROPOSED")
        DecisionStatus.IN_DISCUSSION -> denied("a confirmed decision must pass through NEEDS_RECONSIDERATION")
        DecisionStatus.VOTING -> denied("a confirmed decision cannot re-enter VOTING directly")
        DecisionStatus.CONFIRMED -> allowed()
        DecisionStatus.ON_HOLD ->
            if (transition.actor == DecisionActor.HOST && transition.blockingDependencyRecorded) allowed()
            else denied("only host may place a confirmed decision on hold")
        DecisionStatus.NEEDS_RECONSIDERATION ->
            if (transition.actor in setOf(DecisionActor.HOST, DecisionActor.CO_HOST, DecisionActor.WORKER_SYSTEM) && transition.materialConditionChanged) allowed()
            else denied("reconsideration requires material change and allowed actor")
    }

    private fun fromOnHold(transition: DecisionTransition): DecisionTransitionResult = when (transition.to) {
        DecisionStatus.PROPOSED -> denied("an on-hold decision cannot return to PROPOSED")
        DecisionStatus.IN_DISCUSSION ->
            if (transition.actor in setOf(DecisionActor.HOST, DecisionActor.CO_HOST, DecisionActor.WORKER_SYSTEM)) allowed()
            else denied("actor cannot reopen an on-hold decision")
        DecisionStatus.VOTING ->
            if (transition.actor in setOf(DecisionActor.HOST, DecisionActor.CO_HOST) && transition.voteLinked && transition.voteOpen) allowed()
            else denied("moving to VOTING requires host/co-host and open linked vote")
        DecisionStatus.CONFIRMED ->
            if (transition.actor == DecisionActor.HOST && transition.hasEvidence) allowed()
            else denied("only host may confirm an on-hold decision")
        DecisionStatus.ON_HOLD -> allowed()
        DecisionStatus.NEEDS_RECONSIDERATION -> denied("an on-hold decision cannot jump to reconsideration")
    }

    private fun fromNeedsReconsideration(transition: DecisionTransition): DecisionTransitionResult = when (transition.to) {
        DecisionStatus.PROPOSED -> denied("a reconsideration flow cannot return to PROPOSED")
        DecisionStatus.IN_DISCUSSION ->
            if (transition.actor in setOf(DecisionActor.HOST, DecisionActor.CO_HOST)) allowed()
            else denied("only host/co-host may reopen reconsideration into discussion")
        DecisionStatus.VOTING ->
            if (transition.actor in setOf(DecisionActor.HOST, DecisionActor.CO_HOST) && transition.voteLinked) allowed()
            else denied("moving to VOTING requires host/co-host and linked vote")
        DecisionStatus.CONFIRMED ->
            if (transition.actor == DecisionActor.HOST && transition.hasEvidence) allowed()
            else denied("only host may re-confirm a reconsidered decision")
        DecisionStatus.ON_HOLD -> denied("a reconsideration flow should not move to ON_HOLD directly")
        DecisionStatus.NEEDS_RECONSIDERATION -> allowed()
    }

    private fun allowed() = DecisionTransitionResult(true)

    private fun denied(reason: String) = DecisionTransitionResult(false, reason)
}
