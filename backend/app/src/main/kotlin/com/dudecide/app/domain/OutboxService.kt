package com.dudecide.app.domain

import com.dudecide.app.jooq.enums.OutboxStatus
import com.dudecide.app.jooq.tables.OutboxEvent.Companion.OUTBOX_EVENT
import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.DSLContext
import org.jooq.JSON
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class OutboxService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) {

    fun append(
        aggregateType: String,
        aggregateId: UUID,
        eventType: String,
        payload: Map<String, Any?>,
    ) {
        dsl.insertInto(OUTBOX_EVENT)
            .set(OUTBOX_EVENT.ID, UUID.randomUUID())
            .set(OUTBOX_EVENT.AGGREGATE_TYPE, aggregateType)
            .set(OUTBOX_EVENT.AGGREGATE_ID, aggregateId)
            .set(OUTBOX_EVENT.EVENT_TYPE, eventType)
            .set(OUTBOX_EVENT.PAYLOAD, JSON.valueOf(objectMapper.writeValueAsString(payload)))
            .set(OUTBOX_EVENT.STATUS, OutboxStatus.pending)
            .execute()
    }
}
