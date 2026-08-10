package com.dudecide.app.domain

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NotificationDispatchWorker(
    private val notificationService: NotificationService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${dudecide.workers.notification.fixed-delay-ms:7000}")
    fun dispatchPending() {
        dispatchPendingBatch()
    }

    fun dispatchPendingBatch(limit: Int = 50): Int {
        val pending = notificationService.pendingDispatch(limit)
        pending.forEach { notification ->
            val notificationId = requireNotNull(notification.id)
            logger.info("Dispatching notification type={} meetingId={} recipientUserId={}", notification.type, notification.meetingId, notification.recipientUserId)
            notificationService.markSent(notificationId)
        }
        return pending.size
    }
}
