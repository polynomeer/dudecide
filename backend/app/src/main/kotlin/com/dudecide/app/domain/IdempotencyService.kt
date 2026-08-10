package com.dudecide.app.domain

import com.dudecide.app.jooq.tables.IdempotencyRecord.Companion.IDEMPOTENCY_RECORD
import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.DSLContext
import org.jooq.JSON
import org.jooq.exception.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class IdempotencyService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun <T : Any> execute(
        operationScope: String,
        idempotencyKey: String?,
        responseType: Class<T>,
        supplier: () -> IdempotentResponse<T>,
    ): IdempotentResponse<T> {
        val normalizedKey = idempotencyKey?.trim().orEmpty()
        if (normalizedKey.isBlank()) {
            return supplier()
        }

        find(operationScope, normalizedKey, responseType)?.let { return it }

        val created = supplier()
        return try {
            dsl.insertInto(IDEMPOTENCY_RECORD)
                .set(IDEMPOTENCY_RECORD.ID, UUID.randomUUID())
                .set(IDEMPOTENCY_RECORD.OPERATION_SCOPE, operationScope)
                .set(IDEMPOTENCY_RECORD.IDEMPOTENCY_KEY, normalizedKey)
                .set(IDEMPOTENCY_RECORD.RESPONSE_STATUS, created.status)
                .set(IDEMPOTENCY_RECORD.RESPONSE_BODY, JSON.valueOf(objectMapper.writeValueAsString(created.body)))
                .execute()
            created
        } catch (_: DataAccessException) {
            find(operationScope, normalizedKey, responseType) ?: created
        }
    }

    private fun <T : Any> find(
        operationScope: String,
        idempotencyKey: String,
        responseType: Class<T>,
    ): IdempotentResponse<T>? {
        val record = dsl.selectFrom(IDEMPOTENCY_RECORD)
            .where(IDEMPOTENCY_RECORD.OPERATION_SCOPE.eq(operationScope))
            .and(IDEMPOTENCY_RECORD.IDEMPOTENCY_KEY.eq(idempotencyKey))
            .fetchOne()
            ?: return null

        return IdempotentResponse(
            status = requireNotNull(record.responseStatus),
            body = objectMapper.readValue(requireNotNull(record.responseBody).data(), responseType),
        )
    }
}

data class IdempotentResponse<T : Any>(
    val status: Int,
    val body: T,
)
