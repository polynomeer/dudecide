package com.dudecide.shared.decision

enum class DecisionStatus {
    PROPOSED,
    IN_DISCUSSION,
    VOTING,
    CONFIRMED,
    ON_HOLD,
    NEEDS_RECONSIDERATION,
}

enum class DecisionActor {
    HOST,
    CO_HOST,
    PARTICIPANT,
    AI_SYSTEM,
    WORKER_SYSTEM,
}

data class DecisionTransition(
    val from: DecisionStatus?,
    val to: DecisionStatus,
    val actor: DecisionActor,
    val hasEvidence: Boolean = false,
    val voteLinked: Boolean = false,
    val voteOpen: Boolean = false,
    val voteResolved: Boolean = false,
    val blockingDependencyRecorded: Boolean = false,
    val materialConditionChanged: Boolean = false,
    val hostAuthorizedAutoConfirm: Boolean = false,
)

data class DecisionTransitionResult(
    val allowed: Boolean,
    val reason: String? = null,
)
