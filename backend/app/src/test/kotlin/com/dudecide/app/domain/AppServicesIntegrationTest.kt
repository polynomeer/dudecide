package com.dudecide.app.domain

import com.dudecide.app.api.dto.BudgetRangeDto
import com.dudecide.app.api.dto.ConfirmDecisionRequest
import com.dudecide.app.api.dto.CreateThreadRequest
import com.dudecide.app.api.dto.CreateDecisionRequest
import com.dudecide.app.api.dto.CreateInviteLinkRequest
import com.dudecide.app.api.dto.CreateMessageRequest
import com.dudecide.app.api.dto.CreateMeetingRequest
import com.dudecide.app.api.dto.CreateTaskRequest
import com.dudecide.app.api.dto.CreateVoteOptionRequest
import com.dudecide.app.api.dto.CreateVoteRequest
import com.dudecide.app.api.dto.JoinInviteRequest
import com.dudecide.app.api.dto.ReconsiderDecisionRequest
import com.dudecide.app.api.dto.MeetingStatusDto
import com.dudecide.app.api.dto.UpdateMessageRequest
import com.dudecide.app.api.dto.UpdateMeetingRequest
import com.dudecide.app.api.dto.UpdateTaskRequest
import com.dudecide.app.api.dto.UpdateMembershipRequest
import com.dudecide.app.api.dto.AddDecisionEvidenceRequest
import com.dudecide.app.api.dto.VoteSubmissionItem
import com.dudecide.app.api.dto.VoteSubmissionRequest
import com.dudecide.app.jooq.enums.DecisionStatus
import com.dudecide.app.jooq.enums.MeetingRole
import com.dudecide.app.jooq.enums.ThreadCategory
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.DecisionHistory.Companion.DECISION_HISTORY
import com.dudecide.app.jooq.tables.InviteLink.Companion.INVITE_LINK
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.OutboxEvent.Companion.OUTBOX_EVENT
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.server.ResponseStatusException
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

            val detail = context.meetingService.getMeeting(response.id)
            assertEquals(5, detail.threads.size)
            assertEquals(1, detail.memberCount)
            assertEquals(1, context.dsl.fetchCount(OUTBOX_EVENT, OUTBOX_EVENT.EVENT_TYPE.eq("MeetingCreated")))
        }
    }

    @Test
    fun `meeting service supports invite join and membership updates`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Invite Flow", meetingType = "trip"),
            )

            val invite = context.meetingService.createInviteLink(
                meeting.id,
                CreateInviteLinkRequest(),
            )

            assertEquals(1, context.dsl.fetchCount(INVITE_LINK, INVITE_LINK.MEETING_ID.eq(meeting.id)))

            val joined = context.meetingService.joinWithInvite(
                invite.token,
                JoinInviteRequest(displayName = "Guest User"),
            )

            assertEquals("guest", joined.role)
            assertEquals("accepted", joined.attendanceStatus)

            val updated = context.meetingService.updateMembership(
                meeting.id,
                joined.id,
                UpdateMembershipRequest(
                    role = "participant",
                    attendanceStatus = "tentative",
                ),
            )

            assertEquals("participant", updated.role)
            assertEquals("tentative", updated.attendanceStatus)
            assertEquals(4, context.dsl.fetchCount(OUTBOX_EVENT, OUTBOX_EVENT.AGGREGATE_ID.eq(meeting.id)))
        }
    }

    @Test
    fun `meeting service updates metadata and status`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Mutable Meeting", meetingType = "trip"),
            )

            val updated = context.meetingService.updateMeeting(
                meeting.id,
                UpdateMeetingRequest(
                    title = "Updated Meeting",
                    status = "active",
                    originRegion = "Busan",
                    expectedHeadcount = 8,
                    budget = BudgetRangeDto(min = 100000.0, max = 150000.0),
                ),
            )

            assertEquals("Updated Meeting", updated.title)
            assertEquals(MeetingStatusDto.ACTIVE, updated.status)
            assertEquals("Busan", updated.originRegion)
            assertEquals(8, updated.expectedHeadcount)
            assertEquals(2, context.dsl.fetchCount(OUTBOX_EVENT, OUTBOX_EVENT.AGGREGATE_ID.eq(meeting.id)))
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
            assertEquals(3, context.dsl.fetchCount(OUTBOX_EVENT, OUTBOX_EVENT.AGGREGATE_ID.eq(createdVote.voteId)))
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

    @Test
    fun `decision service creates manual draft and reopens confirmed decisions`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Decision Flow", meetingType = "trip"),
            )
            val threadId = context.findThreadId(meeting.id, ThreadCategory.location)

            val created = context.decisionService.createDecision(
                meeting.id,
                CreateDecisionRequest(
                    threadId = threadId,
                    decisionType = "location",
                    title = "Choose final area",
                    proposedResult = mapOf("candidate" to "Gapyeong"),
                ),
            )

            assertEquals(com.dudecide.shared.decision.DecisionStatus.PROPOSED, created.status)

            context.decisionService.confirmDecision(
                created.id,
                ConfirmDecisionRequest(
                    confirmedResult = mapOf("selectedArea" to "Gapyeong"),
                    reason = "No travel blockers remained",
                ),
            )

            val reconsidered = context.decisionService.reconsiderDecision(
                created.id,
                ReconsiderDecisionRequest(
                    reason = "Selected package sold out on 2026-08-10",
                ),
            )

            assertEquals(com.dudecide.shared.decision.DecisionStatus.NEEDS_RECONSIDERATION, reconsidered.status)

            val historyActions = context.dsl.select(DECISION_HISTORY.ACTION)
                .from(DECISION_HISTORY)
                .where(DECISION_HISTORY.DECISION_ID.eq(created.id))
                .orderBy(DECISION_HISTORY.CREATED_AT.asc())
                .fetch(DECISION_HISTORY.ACTION)
                .map { requireNotNull(it).literal }

            assertEquals(listOf("drafted", "confirmed", "reopened"), historyActions)
            assertEquals(3, context.dsl.fetchCount(OUTBOX_EVENT, OUTBOX_EVENT.AGGREGATE_ID.eq(created.id)))
        }
    }

    @Test
    fun `message service lists posted messages and writes outbox`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Message Flow", meetingType = "trip"),
            )
            val threadId = context.findThreadId(meeting.id, ThreadCategory.free_chat)

            val posted = context.messageService.postMessage(
                threadId,
                CreateMessageRequest(
                    body = "First planning note",
                    attachments = listOf(mapOf("type" to "link", "url" to "https://example.com")),
                ),
            )

            val messages = context.messageService.listMessages(threadId)
            assertEquals(1, messages.size)
            assertEquals(posted.id, messages.first().id)
            assertEquals("First planning note", messages.first().body)
            assertEquals(1, context.dsl.fetchCount(OUTBOX_EVENT, OUTBOX_EVENT.EVENT_TYPE.eq("MessageCreated")))
        }
    }

    @Test
    fun `message service paginates older thread messages`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Pagination Flow", meetingType = "trip"),
            )
            val threadId = context.findThreadId(meeting.id, ThreadCategory.free_chat)

            repeat(3) { index ->
                context.messageService.postMessage(
                    threadId,
                    CreateMessageRequest(body = "Message ${index + 1}"),
                )
            }

            val latestTwo = context.messageService.listMessages(threadId, limit = 2)
            assertEquals(listOf("Message 2", "Message 3"), latestTwo.map { it.body })

            val older = context.messageService.listMessages(
                threadId = threadId,
                before = latestTwo.first().createdAt,
                limit = 2,
            )
            assertEquals(listOf("Message 1"), older.map { it.body })
        }
    }

    @Test
    fun `thread message and evidence flows are connected`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Collab Flow", meetingType = "trip"),
            )

            val thread = context.threadService.createThread(
                meeting.id,
                CreateThreadRequest(category = "tasking", title = "Packing checklist"),
            )

            val posted = context.messageService.postMessage(
                thread.id,
                CreateMessageRequest(body = "Bring life jackets and a first-aid kit"),
            )
            val updated = context.messageService.updateMessage(
                posted.id,
                UpdateMessageRequest(body = "Bring life jackets, a first-aid kit, and flashlights"),
            )

            val decision = context.decisionService.createDecision(
                meeting.id,
                CreateDecisionRequest(
                    threadId = thread.id,
                    decisionType = "task",
                    title = "Packing responsibilities",
                ),
            )
            val evidence = context.decisionService.addEvidence(
                decision.id,
                AddDecisionEvidenceRequest(
                    messageId = updated.id,
                    evidenceType = "support",
                ),
            )

            assertEquals(thread.id, evidence.threadId)
            assertEquals(updated.id, evidence.messageId)
            assertEquals(1, context.dsl.fetchCount(com.dudecide.app.jooq.tables.DecisionEvidence.Companion.DECISION_EVIDENCE))
        }
    }

    @Test
    fun `report service materializes latest meeting snapshot`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Report Flow", meetingType = "trip"),
            )
            val threadId = context.findThreadId(meeting.id, ThreadCategory.date)

            context.decisionService.createDecision(
                meeting.id,
                CreateDecisionRequest(
                    threadId = threadId,
                    decisionType = "date",
                    title = "Pick final weekend",
                    proposedResult = mapOf("candidate" to "2026-09-12"),
                ),
            )

            val refreshed = context.reportService.refreshReport(meeting.id)
            val latest = context.reportService.getLatestReport(meeting.id)

            assertEquals(1, refreshed.version)
            assertEquals(refreshed.id, latest.id)
            assertEquals("Report Flow", ((latest.snapshot["meeting"] as Map<*, *>)["title"]))
            assertEquals(1, context.dsl.fetchCount(OUTBOX_EVENT, OUTBOX_EVENT.EVENT_TYPE.eq("ReportRefreshed")))
        }
    }

    @Test
    fun `search task and notification flows work together`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Ops Flow", meetingType = "trip"),
            )
            val threadId = context.findThreadId(meeting.id, ThreadCategory.free_chat)
            context.messageService.postMessage(
                threadId,
                CreateMessageRequest(body = "Need to reserve a van before Friday"),
            )

            val task = context.taskService.createTask(
                meeting.id,
                CreateTaskRequest(title = "Reserve a van"),
            )
            val updatedTask = context.taskService.updateTask(
                task.id,
                UpdateTaskRequest(status = "in_progress"),
            )

            context.notificationService.createNotification(
                meeting.id,
                com.dudecide.app.jooq.enums.NotificationType.discussion_stalled,
                payload = mapOf("threadId" to threadId),
            )

            val search = context.searchService.search(meeting.id, "van")
            val notifications = context.notificationService.listNotifications(meeting.id)

            assertEquals(1, search.messages.size)
            assertEquals(1, search.tasks.size)
            assertEquals("in_progress", updatedTask.status)
            assertEquals(1, notifications.size)
        }
    }

    @Test
    fun `participant cannot execute host only meeting mutations`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Access Flow", meetingType = "trip"),
            )
            val invite = context.meetingService.createInviteLink(meeting.id, CreateInviteLinkRequest())
            val joined = context.meetingService.joinWithInvite(
                invite.token,
                JoinInviteRequest(displayName = "Participant User"),
            )

            val error = withActor(joined.userId, "Participant User") {
                assertThrows(ResponseStatusException::class.java) {
                    context.meetingService.createInviteLink(meeting.id, CreateInviteLinkRequest())
                }
            }

            assertEquals(403, error.statusCode.value())
            val detail = withActor(joined.userId, "Participant User") {
                context.meetingService.getMeeting(meeting.id)
            }
            assertEquals(meeting.id, detail.id)
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
        val eventStreamService = EventStreamService(objectMapper)
        val localActorProvider = LocalActorProvider(dsl)
        val meetingAccessService = MeetingAccessService(dsl, localActorProvider)
        val outboxService = OutboxService(dsl, objectMapper)
        val notificationService = NotificationService(dsl, objectMapper, localActorProvider, meetingAccessService, eventStreamService)

        return TestContext(
            dsl = dsl,
            objectMapper = objectMapper,
            meetingService = MeetingService(dsl, localActorProvider, meetingAccessService, outboxService, eventStreamService, notificationService),
            voteService = VoteService(dsl, objectMapper, localActorProvider, meetingAccessService, outboxService, eventStreamService, notificationService),
            decisionService = DecisionService(dsl, objectMapper, localActorProvider, meetingAccessService, outboxService, eventStreamService, notificationService),
            messageService = MessageService(dsl, objectMapper, localActorProvider, meetingAccessService, outboxService, eventStreamService),
            threadService = ThreadService(dsl, meetingAccessService, outboxService, eventStreamService),
            reportService = ReportService(dsl, objectMapper, meetingAccessService, outboxService, eventStreamService, notificationService),
            searchService = SearchService(dsl, meetingAccessService),
            taskService = TaskService(dsl, meetingAccessService, outboxService, eventStreamService),
            notificationService = notificationService,
        )
    }

    private fun <T> withActor(actorUserId: UUID, displayName: String, block: () -> T): T {
        val previous: RequestAttributes? = RequestContextHolder.getRequestAttributes()
        val request = MockHttpServletRequest().apply {
            addHeader("X-Actor-Id", actorUserId.toString())
            addHeader("X-Actor-Name", displayName)
        }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        return try {
            block()
        } finally {
            if (previous != null) {
                RequestContextHolder.setRequestAttributes(previous)
            } else {
                RequestContextHolder.resetRequestAttributes()
            }
        }
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

            CREATE TABLE invite_link (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                meeting_id UUID NOT NULL,
                token VARCHAR(255) NOT NULL,
                status VARCHAR(32) NOT NULL DEFAULT 'active',
                expires_at TIMESTAMP WITH TIME ZONE,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT invite_link_meeting_fk FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
                CONSTRAINT invite_link_token_uk UNIQUE (token)
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

            CREATE TABLE message (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                thread_id UUID NOT NULL,
                sender_user_id UUID NOT NULL,
                body TEXT NOT NULL,
                attachments JSON NOT NULL DEFAULT JSON '[]',
                ai_labels JSON NOT NULL DEFAULT JSON '{}',
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                edited_at TIMESTAMP WITH TIME ZONE,
                CONSTRAINT message_thread_fk FOREIGN KEY (thread_id) REFERENCES topic_thread(id) ON DELETE CASCADE,
                CONSTRAINT message_sender_fk FOREIGN KEY (sender_user_id) REFERENCES app_user(id)
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

            CREATE TABLE decision_evidence (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                decision_id UUID NOT NULL,
                message_id UUID NOT NULL,
                thread_id UUID NOT NULL,
                evidence_type VARCHAR(64) NOT NULL,
                quote_snapshot TEXT NOT NULL,
                confidence NUMERIC(5, 4),
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT decision_evidence_decision_fk FOREIGN KEY (decision_id) REFERENCES decision(id) ON DELETE CASCADE,
                CONSTRAINT decision_evidence_message_fk FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE,
                CONSTRAINT decision_evidence_thread_fk FOREIGN KEY (thread_id) REFERENCES topic_thread(id) ON DELETE CASCADE,
                CONSTRAINT decision_evidence_dedup_uk UNIQUE (decision_id, message_id, evidence_type)
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

            CREATE TABLE task_item (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                meeting_id UUID NOT NULL,
                owner_user_id UUID,
                source_message_id UUID,
                title VARCHAR(200) NOT NULL,
                status VARCHAR(64) NOT NULL DEFAULT 'todo',
                due_at TIMESTAMP WITH TIME ZONE,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT task_item_meeting_fk FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
                CONSTRAINT task_item_owner_fk FOREIGN KEY (owner_user_id) REFERENCES app_user(id),
                CONSTRAINT task_item_source_message_fk FOREIGN KEY (source_message_id) REFERENCES message(id) ON DELETE SET NULL
            );

            CREATE TABLE meeting_report (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                meeting_id UUID NOT NULL,
                version INTEGER NOT NULL,
                snapshot JSON NOT NULL DEFAULT JSON '{}',
                generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT meeting_report_meeting_fk FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
                CONSTRAINT meeting_report_meeting_version_uk UNIQUE (meeting_id, version)
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
        """.trimIndent()
    }

    private data class TestContext(
        val dsl: DSLContext,
        val objectMapper: ObjectMapper,
        val meetingService: MeetingService,
        val voteService: VoteService,
        val decisionService: DecisionService,
        val messageService: MessageService,
        val threadService: ThreadService,
        val reportService: ReportService,
        val searchService: SearchService,
        val taskService: TaskService,
        val notificationService: NotificationService,
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
