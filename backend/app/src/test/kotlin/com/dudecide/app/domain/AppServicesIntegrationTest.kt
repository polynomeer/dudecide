package com.dudecide.app.domain

import com.dudecide.app.api.dto.BudgetRangeDto
import com.dudecide.app.api.dto.ConfirmDecisionRequest
import com.dudecide.app.api.dto.CreateMeetingRequest
import com.dudecide.app.api.dto.CreateVoteOptionRequest
import com.dudecide.app.api.dto.CreateVoteRequest
import com.dudecide.app.api.dto.VoteSubmissionItem
import com.dudecide.app.api.dto.VoteSubmissionRequest
import com.dudecide.app.jooq.enums.DecisionStatus
import com.dudecide.app.jooq.enums.MeetingRole
import com.dudecide.app.jooq.enums.ThreadCategory
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.DecisionHistory.Companion.DECISION_HISTORY
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import com.dudecide.app.jooq.tables.Vote.Companion.VOTE
import com.dudecide.app.jooq.tables.VoteOption.Companion.VOTE_OPTION
import com.fasterxml.jackson.databind.ObjectMapper
import org.h2.jdbcx.JdbcDataSource
import org.h2.tools.RunScript
import org.jooq.DSLContext
import org.jooq.JSON
import org.jooq.SQLDialect
import org.jooq.conf.RenderNameCase
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.util.UUID

class AppServicesIntegrationTest {

    @Test
    fun `meeting service creates owner membership and default threads`() {
        testContext().use { context ->
            val response = context.meetingService.createMeeting(
                CreateMeetingRequest(
                    title = "2026 Autumn Camp",
                    meetingType = "trip",
                    expectedHeadcount = 6,
                    originRegion = "Seoul",
                    budget = BudgetRangeDto(min = 120000.0, max = 180000.0),
                ),
            )

            assertEquals("2026 Autumn Camp", response.title)
            assertEquals(1, context.dsl.fetchCount(MEMBERSHIP, MEMBERSHIP.MEETING_ID.eq(response.id)))
            assertEquals(5, context.dsl.fetchCount(TOPIC_THREAD, TOPIC_THREAD.MEETING_ID.eq(response.id)))

            val hostRole = context.dsl.select(MEMBERSHIP.ROLE)
                .from(MEMBERSHIP)
                .where(MEMBERSHIP.MEETING_ID.eq(response.id))
                .fetchOne(MEMBERSHIP.ROLE)
            assertEquals(MeetingRole.host, hostRole)
        }
    }

    @Test
    fun `vote service creates vote snapshots and closes with ranked results`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Spring Hike", meetingType = "outing"),
            )
            val threadId = context.findThreadId(meeting.id, ThreadCategory.date)
            context.addParticipant(meeting.id, "Second Member")

            val createdVote = context.voteService.createVote(
                meeting.id,
                CreateVoteRequest(
                    threadId = threadId,
                    title = "Pick hike date",
                    voteType = "date_matrix",
                    options = listOf(
                        CreateVoteOptionRequest(optionType = "date", label = "2026-09-12", payload = mapOf("date" to "2026-09-12")),
                        CreateVoteOptionRequest(optionType = "date", label = "2026-09-19", payload = mapOf("date" to "2026-09-19")),
                    ),
                ),
            )

            assertEquals("date_matrix", createdVote.voteType)
            assertEquals(2, createdVote.options.size)
            assertNotNull(createdVote.decisionId)

            val updatedVote = context.voteService.submitResponses(
                createdVote.voteId,
                VoteSubmissionRequest(
                    responses = listOf(
                        VoteSubmissionItem(createdVote.options[0].id, "available"),
                        VoteSubmissionItem(createdVote.options[1].id, "maybe"),
                    ),
                ),
            )

            assertEquals(1, updatedVote.aggregates?.respondedParticipants)
            assertEquals(1, updatedVote.aggregates?.unresolvedParticipants)
            assertEquals(createdVote.options[0].id, updatedVote.aggregates?.leadingOptionIds?.first())

            val closedVote = context.voteService.closeVote(createdVote.voteId)
            assertEquals("CLOSED", closedVote.status.name)

            val decisionRecord = context.dsl.selectFrom(DECISION)
                .where(DECISION.ID.eq(createdVote.decisionId))
                .fetchOne()!!
            val proposedResult = context.readJson(decisionRecord.proposedResult)
            val voteSnapshot = proposedResult["vote"] as Map<*, *>

            assertEquals("closed", voteSnapshot["status"])
            assertTrue((voteSnapshot["leadingOptionIds"] as List<*>).isNotEmpty())
            assertEquals(DecisionStatus.voting, decisionRecord.status)
        }
    }

    @Test
    fun `decision service confirms linked vote result and writes history`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Winter Drive", meetingType = "trip"),
            )
            val threadId = context.findThreadId(meeting.id, ThreadCategory.location)

            val vote = context.voteService.createVote(
                meeting.id,
                CreateVoteRequest(
                    threadId = threadId,
                    title = "Choose lodging area",
                    voteType = "package_preference",
                    options = listOf(
                        CreateVoteOptionRequest(
                            optionType = "package",
                            label = "Gapyeong",
                            payload = mapOf("maxBudget" to 180000, "travelMinutes" to 90),
                        ),
                        CreateVoteOptionRequest(
                            optionType = "package",
                            label = "Yangpyeong",
                            payload = mapOf("maxBudget" to 220000, "travelMinutes" to 120),
                        ),
                    ),
                ),
            )

            context.voteService.submitResponses(
                vote.voteId,
                VoteSubmissionRequest(
                    responses = listOf(
                        VoteSubmissionItem(vote.options[0].id, "good"),
                        VoteSubmissionItem(vote.options[1].id, "dislike"),
                    ),
                ),
            )
            context.voteService.closeVote(vote.voteId)

            val confirmed = context.decisionService.confirmDecision(
                vote.decisionId!!,
                ConfirmDecisionRequest(
                    confirmedResult = mapOf(
                        "selectedOptionId" to vote.options[0].id,
                        "selectedLabel" to vote.options[0].label,
                    ),
                    reason = "Host finalized the least-opposed package",
                ),
            )

            assertEquals(com.dudecide.shared.decision.DecisionStatus.CONFIRMED, confirmed.status)
            assertEquals(1, context.dsl.fetchCount(DECISION_HISTORY, DECISION_HISTORY.DECISION_ID.eq(vote.decisionId)))

            val storedDecision = context.dsl.selectFrom(DECISION)
                .where(DECISION.ID.eq(vote.decisionId))
                .fetchOne()!!
            val confirmedResult = context.readJson(storedDecision.confirmedResult)

            assertEquals(vote.options[0].label, confirmedResult["selectedLabel"])
            assertEquals(DecisionStatus.confirmed, storedDecision.status)
        }
    }

    private fun testContext(): TestContext {
        val dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }
        dataSource.connection.use { connection ->
            RunScript.execute(connection, StringReader(transformedSchemaSql()))
        }

        val objectMapper = ObjectMapper().findAndRegisterModules()
        val dsl = DSL.using(
            dataSource,
            SQLDialect.H2,
            Settings()
                .withRenderNameCase(RenderNameCase.LOWER),
        )
        val localActorProvider = LocalActorProvider(dsl)

        return TestContext(
            dsl = dsl,
            objectMapper = objectMapper,
            meetingService = MeetingService(dsl, localActorProvider),
            voteService = VoteService(dsl, objectMapper, localActorProvider),
            decisionService = DecisionService(dsl, objectMapper, localActorProvider),
        )
    }

    private fun transformedSchemaSql(): String {
        return """
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
                origin_region VARCHAR(120),
                expected_headcount INTEGER,
                budget_min NUMERIC(12, 2),
                budget_max NUMERIC(12, 2),
                starts_at TIMESTAMP WITH TIME ZONE,
                ends_at TIMESTAMP WITH TIME ZONE,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT meeting_owner_fk FOREIGN KEY (owner_user_id) REFERENCES app_user(id)
            );

            CREATE TABLE membership (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                meeting_id UUID NOT NULL,
                user_id UUID NOT NULL,
                role VARCHAR(64) NOT NULL,
                attendance_status VARCHAR(64) NOT NULL DEFAULT 'pending',
                joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT membership_meeting_fk FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
                CONSTRAINT membership_user_fk FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
                CONSTRAINT membership_meeting_user_uk UNIQUE (meeting_id, user_id)
            );

            CREATE TABLE topic_thread (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                meeting_id UUID NOT NULL,
                category VARCHAR(64) NOT NULL,
                title VARCHAR(200) NOT NULL,
                status VARCHAR(64) NOT NULL DEFAULT 'active',
                sort_order INTEGER NOT NULL DEFAULT 0,
                last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT topic_thread_meeting_fk FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE
            );

            CREATE TABLE decision (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                meeting_id UUID NOT NULL,
                thread_id UUID NOT NULL,
                decided_by_user_id UUID,
                decision_type VARCHAR(64) NOT NULL,
                title VARCHAR(200) NOT NULL,
                status VARCHAR(64) NOT NULL DEFAULT 'proposed',
                proposed_result JSON NOT NULL DEFAULT JSON '{}',
                confirmed_result JSON NOT NULL DEFAULT JSON '{}',
                decided_at TIMESTAMP WITH TIME ZONE,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT decision_meeting_fk FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
                CONSTRAINT decision_thread_fk FOREIGN KEY (thread_id) REFERENCES topic_thread(id) ON DELETE CASCADE,
                CONSTRAINT decision_actor_fk FOREIGN KEY (decided_by_user_id) REFERENCES app_user(id)
            );

            CREATE TABLE decision_history (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                decision_id UUID NOT NULL,
                actor_user_id UUID,
                action VARCHAR(64) NOT NULL,
                before_state JSON NOT NULL DEFAULT JSON '{}',
                after_state JSON NOT NULL DEFAULT JSON '{}',
                reason TEXT,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT decision_history_decision_fk FOREIGN KEY (decision_id) REFERENCES decision(id) ON DELETE CASCADE,
                CONSTRAINT decision_history_actor_fk FOREIGN KEY (actor_user_id) REFERENCES app_user(id)
            );

            CREATE TABLE vote (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                meeting_id UUID NOT NULL,
                decision_id UUID,
                thread_id UUID NOT NULL,
                vote_type VARCHAR(64) NOT NULL,
                status VARCHAR(64) NOT NULL DEFAULT 'draft',
                scoring_rule JSON NOT NULL DEFAULT JSON '{}',
                opens_at TIMESTAMP WITH TIME ZONE,
                deadline_at TIMESTAMP WITH TIME ZONE,
                closed_at TIMESTAMP WITH TIME ZONE,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT vote_meeting_fk FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
                CONSTRAINT vote_decision_fk FOREIGN KEY (decision_id) REFERENCES decision(id) ON DELETE SET NULL,
                CONSTRAINT vote_thread_fk FOREIGN KEY (thread_id) REFERENCES topic_thread(id) ON DELETE CASCADE
            );

            CREATE TABLE vote_option (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                vote_id UUID NOT NULL,
                option_type VARCHAR(32) NOT NULL,
                label VARCHAR(200) NOT NULL,
                payload JSON NOT NULL DEFAULT JSON '{}',
                sort_order INTEGER NOT NULL DEFAULT 0,
                CONSTRAINT vote_option_vote_fk FOREIGN KEY (vote_id) REFERENCES vote(id) ON DELETE CASCADE
            );

            CREATE TABLE vote_response (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                vote_id UUID NOT NULL,
                option_id UUID NOT NULL,
                user_id UUID NOT NULL,
                response_value VARCHAR(64) NOT NULL,
                response_payload JSON NOT NULL DEFAULT JSON '{}',
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT vote_response_vote_fk FOREIGN KEY (vote_id) REFERENCES vote(id) ON DELETE CASCADE,
                CONSTRAINT vote_response_option_fk FOREIGN KEY (option_id) REFERENCES vote_option(id) ON DELETE CASCADE,
                CONSTRAINT vote_response_user_fk FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
                CONSTRAINT vote_response_vote_option_user_uk UNIQUE (vote_id, option_id, user_id)
            );
        """.trimIndent()
    }

    private data class TestContext(
        val dsl: DSLContext,
        val objectMapper: ObjectMapper,
        val meetingService: MeetingService,
        val voteService: VoteService,
        val decisionService: DecisionService,
    ) : AutoCloseable {
        override fun close() = Unit

        fun findThreadId(meetingId: UUID, category: ThreadCategory): UUID =
            dsl.select(TOPIC_THREAD.ID)
                .from(TOPIC_THREAD)
                .where(TOPIC_THREAD.MEETING_ID.eq(meetingId))
                .and(TOPIC_THREAD.CATEGORY.eq(category))
                .fetchOne(TOPIC_THREAD.ID)
                ?: error("thread not found for $category")

        fun addParticipant(meetingId: UUID, displayName: String): UUID {
            val userId = UUID.randomUUID()
            dsl.insertInto(com.dudecide.app.jooq.tables.AppUser.Companion.APP_USER)
                .set(com.dudecide.app.jooq.tables.AppUser.Companion.APP_USER.ID, userId)
                .set(com.dudecide.app.jooq.tables.AppUser.Companion.APP_USER.DISPLAY_NAME, displayName)
                .set(com.dudecide.app.jooq.tables.AppUser.Companion.APP_USER.STATUS, "active")
                .execute()

            dsl.insertInto(MEMBERSHIP)
                .set(MEMBERSHIP.ID, UUID.randomUUID())
                .set(MEMBERSHIP.MEETING_ID, meetingId)
                .set(MEMBERSHIP.USER_ID, userId)
                .set(MEMBERSHIP.ROLE, MeetingRole.participant)
                .execute()

            return userId
        }

        fun readJson(value: JSON?): Map<String, Any?> {
            if (value == null || value.data().isBlank()) {
                return emptyMap()
            }
            @Suppress("UNCHECKED_CAST")
            return objectMapper.readValue(value.data(), Map::class.java) as Map<String, Any?>
        }
    }
}
