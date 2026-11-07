package com.dudecide.app.domain.suggestion

import com.dudecide.app.config.DecisionSuggestionProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DecisionSuggestionWorker(
    private val analyzer: DecisionSuggestionAnalyzer,
    private val properties: DecisionSuggestionProperties,
) {

    @Scheduled(fixedDelayString = "\${dudecide.workers.decision-suggestions.fixed-delay-ms:60000}")
    fun analyzeQuietThreads() {
        if (properties.enabled) {
            analyzeBatch()
        }
    }

    fun analyzeBatch(limit: Int = 5): Int {
        var analyzed = 0
        while (analyzed < limit) {
            val threadId = analyzer.claimNext() ?: break
            analyzer.analyze(threadId)
            analyzed++
        }
        return analyzed
    }
}
