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

class WorkerFlowTest {

    @Test
    fun `outbox relay marks pending events as published`() {
        val dsl = dsl()
        val outboxService = OutboxService(dsl, ObjectMapper().findAndRegisterModules())
        val worker = OutboxRelayWorker(outboxService)

        outboxService.append(
            aggregateType = "meeting",
            aggregateId = UUID.randomUUID(),
            eventType = "MeetingCreated",
            payload = mapOf("title" to "Relay Check"),
        )

        assertEquals(1, worker.relayPendingBatch())
        assertEquals(0, outboxService.pending().size)
    }

    @Test
    fun `notification worker marks unsent notifications as sent`() {
        val dsl = dsl()
        val objectMapper = ObjectMapper().findAndRegisterModules()
        val eventStreamService = EventStreamService(objectMapper)
        val localActorProvider = LocalActorProvider(dsl)
        val notificationService = NotificationService(dsl, objectMapper, localActorProvider, eventStreamService)
        val worker = NotificationDispatchWorker(notificationService)

        val meetingId = UUID.randomUUID()
        val recipientId = localActorProvider.ensureHostUser()

        dsl.insertInto(com.dudecide.app.jooq.tables.Meeting.Companion.MEETING)
            .set(com.dudecide.app.jooq.tables.Meeting.Companion.MEETING.ID, meetingId)
            .set(com.dudecide.app.jooq.tables.Meeting.Companion.MEETING.OWNER_USER_ID, recipientId)
            .set(com.dudecide.app.jooq.tables.Meeting.Companion.MEETING.TITLE, "Worker Meeting")
            .set(com.dudecide.app.jooq.tables.Meeting.Companion.MEETING.MEETING_TYPE, "trip")
            .set(com.dudecide.app.jooq.tables.Meeting.Companion.MEETING.STATUS, com.dudecide.app.jooq.enums.MeetingStatus.draft)
            .execute()

        notificationService.createNotification(
            meetingId = meetingId,
            type = com.dudecide.app.jooq.enums.NotificationType.report_ready,
            payload = mapOf("version" to 1),
        )

        assertEquals(1, worker.dispatchPendingBatch())
        assertEquals(0, notificationService.pendingDispatch().size)
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
                    CREATE TABLE app_user (
                        id UUID DEFAULT random_uuid() PRIMARY KEY,
                        display_name VARCHAR(120) NOT NULL,
                        avatar_url TEXT,
                        status VARCHAR(32) NOT NULL DEFAULT 'active',
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                    );
                    CREATE TABLE meeting (
                        id UUID DEFAULT random_uuid() PRIMARY KEY,
                        owner_user_id UUID NOT NULL,
                        title VARCHAR(200) NOT NULL,
                        meeting_type VARCHAR(32) NOT NULL,
                        status VARCHAR(64) NOT NULL DEFAULT 'draft',
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT meeting_owner_fk FOREIGN KEY (owner_user_id) REFERENCES app_user(id)
                    );
                    CREATE TABLE notification (
                        id UUID DEFAULT random_uuid() PRIMARY KEY,
                        meeting_id UUID NOT NULL,
                        recipient_user_id UUID NOT NULL,
                        type VARCHAR(64) NOT NULL,
                        payload JSON NOT NULL DEFAULT JSON '{}',
                        sent_at TIMESTAMP WITH TIME ZONE,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT notification_meeting_fk FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
                        CONSTRAINT notification_recipient_fk FOREIGN KEY (recipient_user_id) REFERENCES app_user(id) ON DELETE CASCADE
                    );
                    CREATE TABLE outbox_event (
                        id UUID DEFAULT random_uuid() PRIMARY KEY,
                        aggregate_type VARCHAR(64) NOT NULL,
                        aggregate_id UUID NOT NULL,
                        event_type VARCHAR(128) NOT NULL,
                        payload JSON NOT NULL,
                        status VARCHAR(64) NOT NULL DEFAULT 'pending',
                        occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        published_at TIMESTAMP WITH TIME ZONE
                    );
                    """.trimIndent(),
                ),
            )
        }
        DSL.using(dataSource, SQLDialect.H2)
    }
}
