package com.dudecide.app.domain

import com.fasterxml.jackson.databind.ObjectMapper
import org.h2.jdbcx.JdbcDataSource
import org.h2.tools.RunScript
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class IdempotencyServiceTest {

    @Test
    fun `returns cached response for same scope and key`() {
        val dsl = dsl()
        val service = IdempotencyService(dsl, ObjectMapper().findAndRegisterModules())
        val counter = AtomicInteger(0)

        val first = service.execute(
            operationScope = "meetings.create",
            idempotencyKey = "same-key",
            responseType = SampleResponse::class.java,
        ) {
            counter.incrementAndGet()
            IdempotentResponse(201, SampleResponse("first"))
        }
        val second = service.execute(
            operationScope = "meetings.create",
            idempotencyKey = "same-key",
            responseType = SampleResponse::class.java,
        ) {
            counter.incrementAndGet()
            IdempotentResponse(201, SampleResponse("second"))
        }

        assertEquals(1, counter.get())
        assertEquals("first", first.body.value)
        assertEquals("first", second.body.value)
        assertEquals(1, dsl.fetchCount(com.dudecide.app.jooq.tables.IdempotencyRecord.Companion.IDEMPOTENCY_RECORD))
    }

    @Test
    fun `blank idempotency key bypasses cache`() {
        val dsl = dsl()
        val service = IdempotencyService(dsl, ObjectMapper().findAndRegisterModules())
        val counter = AtomicInteger(0)

        service.execute("reports.refresh", " ", SampleResponse::class.java) {
            counter.incrementAndGet()
            IdempotentResponse(201, SampleResponse("a"))
        }
        service.execute("reports.refresh", null, SampleResponse::class.java) {
            counter.incrementAndGet()
            IdempotentResponse(201, SampleResponse("b"))
        }

        assertEquals(2, counter.get())
        assertEquals(0, dsl.fetchCount(com.dudecide.app.jooq.tables.IdempotencyRecord.Companion.IDEMPOTENCY_RECORD))
    }

    private fun dsl() = JdbcDataSource().apply {
        setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
        user = "sa"
        password = ""
    }.let { dataSource ->
        dataSource.connection.use { connection ->
            RunScript.execute(
                connection,
                StringReader(
                    """
                    CREATE TABLE idempotency_record (
                        id UUID DEFAULT random_uuid() PRIMARY KEY,
                        operation_scope VARCHAR(160) NOT NULL,
                        idempotency_key VARCHAR(255) NOT NULL,
                        response_status INTEGER NOT NULL,
                        response_body JSON NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT idempotency_record_scope_key_uk UNIQUE (operation_scope, idempotency_key)
                    );
                    """.trimIndent(),
                ),
            )
        }
        DSL.using(dataSource, SQLDialect.H2)
    }

    private data class SampleResponse(
        val value: String,
    )
}
