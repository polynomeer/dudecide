package com.dudecide.app.domain.chatimport

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ChatImportAnalysisWorker(
    private val analyzer: ChatImportAnalyzer,
) {

    @Scheduled(fixedDelayString = "\${dudecide.workers.chat-import.fixed-delay-ms:3000}")
    fun analyzePending() {
        analyzePendingBatch()
    }

    fun analyzePendingBatch(limit: Int = 5): Int {
        var analyzed = 0
        while (analyzed < limit) {
            val importId = analyzer.claimNext() ?: break
            analyzer.analyze(importId)
            analyzed++
        }
        return analyzed
    }
}
