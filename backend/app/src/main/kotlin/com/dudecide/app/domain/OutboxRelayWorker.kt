package com.dudecide.app.domain

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OutboxRelayWorker(
    private val outboxService: OutboxService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${dudecide.workers.outbox.fixed-delay-ms:5000}")
    fun relayPending() {
        relayPendingBatch()
    }

    fun relayPendingBatch(limit: Int = 50): Int {
        val pending = outboxService.pending(limit)
        pending.forEach { event ->
            val eventId = requireNotNull(event.id)
            logger.info("Publishing outbox event type={} aggregateType={} aggregateId={}", event.eventType, event.aggregateType, event.aggregateId)
            outboxService.markPublished(eventId)
        }
        return pending.size
    }
}
