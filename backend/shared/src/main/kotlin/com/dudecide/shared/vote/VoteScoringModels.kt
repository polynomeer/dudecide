package com.dudecide.shared.vote

import java.time.LocalDate

data class DateVoteCounts(
    val date: LocalDate,
    val available: Int,
    val maybe: Int,
    val unavailable: Int,
)

data class ScoredDateOption(
    val date: LocalDate,
    val rawScore: Double,
    val available: Int,
    val maybe: Int,
    val unavailable: Int,
)

data class LocationVoteCounts(
    val optionId: String,
    val good: Int,
    val okay: Int,
    val dislike: Int,
    val maxBudget: Int,
    val travelMinutes: Int,
)

data class ScoredLocationOption(
    val optionId: String,
    val rawScore: Double,
    val good: Int,
    val okay: Int,
    val dislike: Int,
    val maxBudget: Int,
    val travelMinutes: Int,
)
