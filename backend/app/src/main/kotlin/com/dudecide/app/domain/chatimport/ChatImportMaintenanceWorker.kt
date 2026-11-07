package com.dudecide.app.domain.chatimport

import com.dudecide.app.jooq.enums.ChatImportStatus
import com.dudecide.app.jooq.enums.MeetingStatus
import com.dudecide.app.jooq.tables.ChatImport.Companion.CHAT_IMPORT
import com.dudecide.app.jooq.tables.Meeting.Companion.MEETING
import io.micrometer.core.instrument.MeterRegistry
import org.jooq.DSLContext
import org.jooq.JSON
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.OffsetDateTime

/**
 * Retention and recovery for chat imports:
 * - deletes draft meetings created by guest imports that were never applied (ADR 0005, 7-day retention)
 * - puts imports left in `analyzing` by a crashed process back in the queue, failing them after repeated attempts
 */
@Component
class ChatImportMaintenanceWorker(
    private val dsl: DSLContext,
    private val meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${dudecide.workers.chat-import-purge.fixed-delay-ms:3600000}")
    fun purge() {
        purgeExpired(OffsetDateTime.now())
    }

    @Scheduled(fixedDelayString = "\${dudecide.workers.chat-import-reclaim.fixed-delay-ms:300000}")
    fun reclaim() {
        reclaimStale(OffsetDateTime.now())
    }

    /** Deletes expired draft meetings; everything imported into them cascades away. Returns deleted meetings. */
    @Transactional
    fun purgeExpired(now: OffsetDateTime): Int {
        val meetingIds = dsl.selectDistinct(CHAT_IMPORT.MEETING_ID)
            .from(CHAT_IMPORT)
            .join(MEETING).on(MEETING.ID.eq(CHAT_IMPORT.MEETING_ID))
            .where(CHAT_IMPORT.PURGE_AT.lt(now))
            .and(MEETING.STATUS.eq(MeetingStatus.draft))
            .fetch(CHAT_IMPORT.MEETING_ID)
            .filterNotNull()
        if (meetingIds.isEmpty()) {
            return 0
        }

        val deleted = dsl.deleteFrom(MEETING)
            .where(MEETING.ID.`in`(meetingIds))
            .and(MEETING.STATUS.eq(MeetingStatus.draft))
            .execute()
        meterRegistry.counter("dudecide.import.purged.meetings").increment(deleted.toDouble())
        logger.info("Purged {} expired draft meetings from chat imports", deleted)
        return deleted
    }

    /** Requeues imports whose analysis claim went stale; fails them once they used up their attempts. */
    @Transactional
    fun reclaimStale(now: OffsetDateTime): ReclaimResult {
        val cutoff = now.minus(STALE_AFTER)
        val stale = CHAT_IMPORT.STATUS.eq(ChatImportStatus.analyzing).and(CHAT_IMPORT.CLAIMED_AT.lt(cutoff))

        val failed = dsl.update(CHAT_IMPORT)
            .set(CHAT_IMPORT.STATUS, ChatImportStatus.failed)
            .set(CHAT_IMPORT.FAILURE_REASON, "analysis timed out after $MAX_ATTEMPTS attempts")
            .set(CHAT_IMPORT.COMPLETED_AT, now)
            .where(stale)
            .and(CHAT_IMPORT.ATTEMPTS.ge(MAX_ATTEMPTS))
            .execute()
        val requeued = dsl.update(CHAT_IMPORT)
            .set(CHAT_IMPORT.STATUS, ChatImportStatus.submitted)
            .set(CHAT_IMPORT.PROGRESS, JSON.valueOf("{}"))
            .setNull(CHAT_IMPORT.CLAIMED_AT)
            .where(stale)
            .and(CHAT_IMPORT.ATTEMPTS.lt(MAX_ATTEMPTS))
            .execute()

        if (failed + requeued > 0) {
            meterRegistry.counter("dudecide.import.reclaimed", "outcome", "requeued").increment(requeued.toDouble())
            meterRegistry.counter("dudecide.import.reclaimed", "outcome", "failed").increment(failed.toDouble())
            logger.warn("Reclaimed stale chat import claims requeued={} failed={}", requeued, failed)
        }
        return ReclaimResult(requeued = requeued, failed = failed)
    }

    data class ReclaimResult(val requeued: Int, val failed: Int)

    private companion object {
        val STALE_AFTER: Duration = Duration.ofMinutes(15)
        const val MAX_ATTEMPTS = 2
    }
}
