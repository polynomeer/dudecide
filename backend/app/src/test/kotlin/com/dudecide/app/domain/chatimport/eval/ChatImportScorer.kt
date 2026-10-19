package com.dudecide.app.domain.chatimport.eval

/**
 * Scores one analysis result against gold labels. The headline number is the false-decided rate: telling a group
 * something is decided when it is not costs more trust than missing a decision.
 */
object ChatImportScorer {

    data class CaseScore(
        val caseId: String,
        val predictedDecided: Int,
        val correctDecided: Int,
        val expectedDecided: Int,
        val falseDecided: Int,
        val statusMatches: Int,
        val expectedDecisions: Int,
        val taskOwnersFound: Int,
        val expectedTaskOwners: Int,
        val openQuestionError: Int,
    )

    data class Summary(
        val cases: Int,
        val decidedPrecision: Double,
        val decidedRecall: Double,
        val falseDecidedRate: Double,
        val statusAccuracy: Double,
        val taskRecall: Double,
        val openQuestionMeanError: Double,
    )

    fun score(
        case: ChatImportEvalCase,
        result: Map<String, Any?>,
        pseudonymOf: (String) -> String,
        speakerIdOf: (String) -> String?,
    ): CaseScore {
        @Suppress("UNCHECKED_CAST")
        val items = result["items"] as? List<Map<String, Any?>> ?: emptyList()
        val decisions = items.filter { it["kind"] == "decision" }
        val predictedDecided = decisions.filter { it["status"] == "likely_decided" }
        val expectedDecided = case.expected.decisions.filter { it.status == "likely_decided" }

        val correctDecided = predictedDecided.count { item ->
            expectedDecided.any { it.decisionType == item["decisionType"] && optionMatches(it.option, leadingOption(item)) }
        }
        val falseDecided = predictedDecided.count { item ->
            case.expected.decisions.none { it.decisionType == item["decisionType"] && it.status == "likely_decided" }
        }
        val statusMatches = case.expected.decisions.count { expected ->
            decisions.any { it["decisionType"] == expected.decisionType && it["status"] == expected.status }
        }

        val ownerIds = items.filter { it["kind"] == "task" }.mapNotNull { it["ownerSpeakerId"] as? String }.toSet()
        val taskOwnersFound = case.expected.taskOwners.count { speakerIdOf(pseudonymOf(it)) in ownerIds }
        val openQuestions = items.count { it["kind"] == "open_question" }

        return CaseScore(
            caseId = case.id,
            predictedDecided = predictedDecided.size,
            correctDecided = correctDecided,
            expectedDecided = expectedDecided.size,
            falseDecided = falseDecided,
            statusMatches = statusMatches,
            expectedDecisions = case.expected.decisions.size,
            taskOwnersFound = taskOwnersFound,
            expectedTaskOwners = case.expected.taskOwners.size,
            openQuestionError = kotlin.math.abs(openQuestions - case.expected.openQuestions),
        )
    }

    fun summarize(scores: List<CaseScore>): Summary = Summary(
        cases = scores.size,
        decidedPrecision = ratio(scores.sumOf { it.correctDecided }, scores.sumOf { it.predictedDecided }),
        decidedRecall = ratio(scores.sumOf { it.correctDecided }, scores.sumOf { it.expectedDecided }),
        falseDecidedRate = ratio(scores.sumOf { it.falseDecided }, scores.sumOf { it.predictedDecided }, empty = 0.0),
        statusAccuracy = ratio(scores.sumOf { it.statusMatches }, scores.sumOf { it.expectedDecisions }),
        taskRecall = ratio(scores.sumOf { it.taskOwnersFound }, scores.sumOf { it.expectedTaskOwners }),
        openQuestionMeanError = if (scores.isEmpty()) 0.0 else scores.sumOf { it.openQuestionError }.toDouble() / scores.size,
    )

    private fun leadingOption(item: Map<String, Any?>): String? {
        @Suppress("UNCHECKED_CAST")
        return (item["proposedResult"] as? Map<String, Any?>)?.get("option") as? String
    }

    // Spelling-tolerant: "18일" matches "10월 18일", "25th" matches "the 25th".
    internal fun optionMatches(expected: String?, predicted: String?): Boolean {
        if (expected == null) return true
        if (predicted == null) return false
        val a = normalize(expected)
        val b = normalize(predicted)
        return a.isNotEmpty() && b.isNotEmpty() && (a in b || b in a)
    }

    private fun normalize(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }

    private fun ratio(numerator: Int, denominator: Int, empty: Double = 1.0): Double =
        if (denominator == 0) empty else numerator.toDouble() / denominator
}
