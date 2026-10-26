package com.dudecide.app.domain

import com.dudecide.app.api.dto.ApplyChatImportRequest
import com.dudecide.app.api.dto.BudgetRangeDto
import com.dudecide.app.api.dto.ChatImportConsentInput
import com.dudecide.app.api.dto.ChatImportMessageInput
import com.dudecide.app.api.dto.ChatImportRangeInput
import com.dudecide.app.api.dto.ChatImportSourceInput
import com.dudecide.app.api.dto.ChatImportSpeakerInput
import com.dudecide.app.api.dto.ClaimImportSpeakerRequest
import com.dudecide.app.api.dto.CreateChatImportRequest
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
import com.dudecide.app.api.dto.UpdateImportSpeakerRequest
import com.dudecide.app.api.dto.UpdateMessageRequest
import com.dudecide.app.api.dto.UpdateMeetingRequest
import com.dudecide.app.api.dto.UpdateTaskRequest
import com.dudecide.app.api.dto.UpdateMembershipRequest
import com.dudecide.app.api.dto.AddDecisionEvidenceRequest
import com.dudecide.app.api.dto.VoteSubmissionItem
import com.dudecide.app.api.dto.VoteSubmissionRequest
import com.dudecide.app.config.ChatImportAiProperties
import com.dudecide.app.config.ChatImportLimitProperties
import com.dudecide.app.domain.chatimport.ChatImportAnalysisWorker
import com.dudecide.app.domain.chatimport.ChatImportAnalyzer
import com.dudecide.app.domain.chatimport.ChatImportApplyService
import com.dudecide.app.domain.chatimport.ChatImportMaintenanceWorker
import com.dudecide.app.domain.chatimport.ChatImportService
import com.dudecide.app.domain.chatimport.EpisodeMapRequest
import com.dudecide.app.domain.chatimport.EpisodeMapResult
import com.dudecide.app.domain.chatimport.FakeLlmClient
import com.dudecide.app.domain.chatimport.ImportSpeakerService
import com.dudecide.app.domain.chatimport.InvitePreviewService
import com.dudecide.app.domain.chatimport.LlmClient
import com.dudecide.app.domain.chatimport.SubjectReduceRequest
import com.dudecide.app.domain.chatimport.SubjectReduceResult
import com.dudecide.app.jooq.enums.ChatImportSource
import com.dudecide.app.jooq.enums.ChatImportStatus
import com.dudecide.app.jooq.enums.DecisionStatus
import com.dudecide.app.jooq.enums.MeetingRole
import com.dudecide.app.jooq.enums.MeetingStatus
import com.dudecide.app.jooq.enums.MessageOrigin
import com.dudecide.app.jooq.enums.ThreadCategory
import com.dudecide.app.jooq.tables.ChatImport.Companion.CHAT_IMPORT
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.DecisionEvidence.Companion.DECISION_EVIDENCE
import com.dudecide.app.jooq.tables.MeetingReport.Companion.MEETING_REPORT
import com.dudecide.app.jooq.tables.TaskItem.Companion.TASK_ITEM
import com.dudecide.app.jooq.tables.DecisionHistory.Companion.DECISION_HISTORY
import com.dudecide.app.jooq.tables.ImportSpeaker.Companion.IMPORT_SPEAKER
import com.dudecide.app.jooq.tables.InviteLink.Companion.INVITE_LINK
import com.dudecide.app.jooq.tables.Meeting.Companion.MEETING
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.Message.Companion.MESSAGE
import com.dudecide.app.jooq.tables.OutboxEvent.Companion.OUTBOX_EVENT
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import com.dudecide.app.jooq.tables.Vote.Companion.VOTE
import com.dudecide.app.jooq.tables.VoteOption.Companion.VOTE_OPTION
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.h2.jdbcx.JdbcDataSource
import org.h2.tools.RunScript
import org.jooq.DSLContext
import org.jooq.JSON
import org.jooq.SQLDialect
import org.jooq.conf.RenderNameCase
import org.jooq.conf.Settings
import org.jooq.exception.DataAccessException
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
import java.time.OffsetDateTime
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
    fun `meeting service stores event dates on create and rejects reversed ranges`() {
        testContext().use { context ->
            val startsAt = OffsetDateTime.parse("2026-11-14T09:00:00+09:00")
            val endsAt = OffsetDateTime.parse("2026-11-16T21:00:00+09:00")
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(
                    title = "Dated Trip",
                    meetingType = "trip",
                    budget = BudgetRangeDto(min = 300000.0, max = 500000.0),
                    startsAt = startsAt,
                    endsAt = endsAt,
                ),
            )

            val detail = context.meetingService.getMeeting(meeting.id)
            assertEquals(startsAt.toInstant(), detail.startsAt?.toInstant())
            assertEquals(endsAt.toInstant(), detail.endsAt?.toInstant())
            assertEquals(300000.0, detail.budget?.min)

            val error = assertThrows(ResponseStatusException::class.java) {
                context.meetingService.createMeeting(
                    CreateMeetingRequest(title = "Backwards", meetingType = "trip", startsAt = endsAt, endsAt = startsAt),
                )
            }
            assertEquals(400, error.statusCode.value())
        }
    }

    @Test
    fun `meeting members are listed with display names for every member`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Member List", meetingType = "trip"),
            )
            val invite = context.meetingService.createInviteLink(meeting.id, CreateInviteLinkRequest())
            val joined = context.meetingService.joinWithInvite(invite.token, JoinInviteRequest(displayName = "Guest Member"))

            val members = withActor(joined.userId, "Guest Member") {
                context.meetingService.listMembers(meeting.id)
            }

            assertEquals(2, members.size)
            assertEquals("host", members.first().role)
            assertEquals(listOf("Guest Member"), members.filter { it.userId == joined.userId }.map { it.displayName })

            val hostJoinNotices = context.notificationService.listNotifications(meeting.id).filter { it.type == "invite_joined" }
            assertEquals(1, hostJoinNotices.size)
            assertEquals(joined.userId.toString(), hostJoinNotices.single().payload["userId"].toString())
            val guestJoinNotices = withActor(joined.userId, "Guest Member") {
                context.notificationService.listNotifications(meeting.id).filter { it.type == "invite_joined" }
            }
            assertTrue(guestJoinNotices.isEmpty())
        }
    }

    @Test
    fun `meeting-wide notifications reach other members but not the actor`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Fan Out", meetingType = "trip"),
            )
            val invite = context.meetingService.createInviteLink(meeting.id, CreateInviteLinkRequest())
            val guest = context.meetingService.joinWithInvite(invite.token, JoinInviteRequest(displayName = "Guest Reader"))
            val threadId = context.findThreadId(meeting.id, ThreadCategory.location)
            val decision = context.decisionService.createDecision(
                meeting.id,
                CreateDecisionRequest(threadId = threadId, decisionType = "location", title = "Final area"),
            )

            context.decisionService.confirmDecision(decision.id, ConfirmDecisionRequest(confirmedResult = mapOf("summary" to "Aewol")))
            context.reportService.refreshReport(meeting.id)

            val hostTypes = context.notificationService.listNotifications(meeting.id).map { it.type }.toSet()
            val guestTypes = withActor(guest.userId, "Guest Reader") {
                context.notificationService.listNotifications(meeting.id).map { it.type }
            }
            assertTrue("decision_confirmed" !in hostTypes && "report_ready" !in hostTypes)
            assertEquals(listOf("decision_confirmed", "report_ready"), guestTypes.filter { it != "invite_joined" }.sorted())
        }
    }

    @Test
    fun `notifications can be marked read one at a time or all at once`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Read State", meetingType = "trip"),
            )
            repeat(3) {
                context.notificationService.createNotification(
                    meeting.id,
                    com.dudecide.app.jooq.enums.NotificationType.discussion_stalled,
                    payload = mapOf("index" to it),
                )
            }
            val initial = context.notificationService.listNotifications(meeting.id)
            assertTrue(initial.all { it.readAt == null })

            val first = context.notificationService.markRead(meeting.id, initial.first().id)
            assertNotNull(first.readAt)
            val again = context.notificationService.markRead(meeting.id, initial.first().id)
            assertEquals(first.readAt?.toInstant(), again.readAt?.toInstant())

            assertEquals(2, context.notificationService.markAllRead(meeting.id).updatedCount)
            assertTrue(context.notificationService.listNotifications(meeting.id).all { it.readAt != null })
            assertEquals(0, context.notificationService.markAllRead(meeting.id).updatedCount)

            val invite = context.meetingService.createInviteLink(meeting.id, CreateInviteLinkRequest())
            val joined = context.meetingService.joinWithInvite(invite.token, JoinInviteRequest(displayName = "Other Member"))
            val error = withActor(joined.userId, "Other Member") {
                assertThrows(ResponseStatusException::class.java) {
                    context.notificationService.markRead(meeting.id, initial.first().id)
                }
            }
            assertEquals(404, error.statusCode.value())
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

    @Test
    fun `imported messages enforce origin rules dedupe and cascade with their import`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Imported Trip", meetingType = "trip"),
            )
            val threadId = context.findThreadId(meeting.id, ThreadCategory.free_chat)
            val seeded = context.seedImport(meeting.id, threadId, context.ownerId(meeting.id))

            context.insertImportedMessage(seeded, "Let's go with the 18th", "a".repeat(64))

            assertThrows(DataAccessException::class.java) {
                context.insertImportedMessage(seeded, "Let's go with the 18th", "a".repeat(64))
            }
            assertThrows(DataAccessException::class.java) {
                context.dsl.insertInto(MESSAGE)
                    .set(MESSAGE.THREAD_ID, threadId)
                    .set(MESSAGE.BODY, "native message without sender")
                    .execute()
            }

            context.dsl.deleteFrom(CHAT_IMPORT).where(CHAT_IMPORT.ID.eq(seeded.importId)).execute()

            assertEquals(0, context.dsl.fetchCount(MESSAGE, MESSAGE.ORIGIN.eq(MessageOrigin.imported)))
        }
    }

    @Test
    fun `imported speaker label follows mapping and uploader visibility`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Speaker Labels", meetingType = "trip"),
            )
            val ownerId = context.ownerId(meeting.id)
            val threadId = context.findThreadId(meeting.id, ThreadCategory.free_chat)
            val seeded = context.seedImport(meeting.id, threadId, ownerId)
            context.insertImportedMessage(seeded, "Aewol works for me", "b".repeat(64))
            val participantId = context.addParticipant(meeting.id, "Participant User")

            val uploaderView = context.messageService.listMessages(threadId).single()
            assertEquals("imported", uploaderView.origin)
            assertEquals(null, uploaderView.senderUserId)
            assertEquals("Minji", uploaderView.speaker.displayName)
            assertEquals(seeded.speakerId, uploaderView.speaker.importSpeakerId)
            assertEquals(false, uploaderView.speaker.pseudonymized)

            val participantView = withActor(participantId, "Participant User") {
                context.messageService.listMessages(threadId).single()
            }
            assertEquals("Participant A", participantView.speaker.displayName)
            assertEquals(true, participantView.speaker.pseudonymized)

            val membershipId = context.dsl.select(MEMBERSHIP.ID)
                .from(MEMBERSHIP)
                .where(MEMBERSHIP.USER_ID.eq(participantId))
                .fetchOne(MEMBERSHIP.ID)
            context.dsl.update(IMPORT_SPEAKER)
                .set(IMPORT_SPEAKER.MEMBERSHIP_ID, membershipId)
                .where(IMPORT_SPEAKER.ID.eq(seeded.speakerId))
                .execute()

            val mappedView = withActor(participantId, "Participant User") {
                context.messageService.listMessages(threadId).single()
            }
            assertEquals("Participant User", mappedView.speaker.displayName)
            assertEquals(participantId, mappedView.speaker.userId)
            assertEquals(false, mappedView.speaker.pseudonymized)
        }
    }

    @Test
    fun `imported messages cannot be edited and read only threads reject posts`() {
        testContext().use { context ->
            val meeting = context.meetingService.createMeeting(
                CreateMeetingRequest(title = "Read Only Import", meetingType = "trip"),
            )
            val ownerId = context.ownerId(meeting.id)
            val threadId = context.findThreadId(meeting.id, ThreadCategory.free_chat)
            val seeded = context.seedImport(meeting.id, threadId, ownerId)
            val importedMessageId = context.insertImportedMessage(seeded, "Book the ferry", "c".repeat(64))

            val editError = assertThrows(ResponseStatusException::class.java) {
                context.messageService.updateMessage(importedMessageId, UpdateMessageRequest(body = "edited"))
            }
            assertEquals(422, editError.statusCode.value())

            val nativeMessage = context.messageService.postMessage(threadId, CreateMessageRequest(body = "Native note"))
            assertEquals("native", nativeMessage.origin)
            assertEquals(ownerId, nativeMessage.speaker.userId)

            context.dsl.update(TOPIC_THREAD)
                .set(TOPIC_THREAD.READ_ONLY, true)
                .where(TOPIC_THREAD.ID.eq(threadId))
                .execute()

            val postError = assertThrows(ResponseStatusException::class.java) {
                context.messageService.postMessage(threadId, CreateMessageRequest(body = "Should fail"))
            }
            assertEquals(422, postError.statusCode.value())
            assertTrue(context.meetingService.getMeeting(meeting.id).threads.single { it.id == threadId }.readOnly)
        }
    }

    @Test
    fun `guest chat import creates a draft meeting with a read only import thread`() {
        testContext().use { context ->
            val created = context.chatImportService.createImport(
                chatImportRequest(
                    messages = listOf(
                        "me" to "Let's go with the 18th",
                        "junho" to "ㅇㅇ",
                        "seoyeon" to "Aewol for lodging?",
                    ),
                ),
            )

            assertEquals("submitted", created.status)
            assertEquals(3, created.messageCount)
            assertEquals(0, created.duplicateCount)

            val meeting = context.dsl.selectFrom(MEETING).where(MEETING.ID.eq(created.meetingId)).fetchOne()!!
            assertEquals("Jeju trip", meeting.title)
            assertEquals(MeetingStatus.draft, meeting.status)

            val thread = context.dsl.selectFrom(TOPIC_THREAD).where(TOPIC_THREAD.ID.eq(created.threadId)).fetchOne()!!
            assertEquals(true, thread.readOnly)
            assertEquals("Imported: Jeju trip", thread.title)
            assertEquals(5, thread.sortOrder)

            val speakers = context.dsl.selectFrom(IMPORT_SPEAKER)
                .where(IMPORT_SPEAKER.THREAD_ID.eq(created.threadId))
                .fetch()
                .associateBy { it.displayName }
            assertEquals(setOf("Participant A", "Participant B", "Participant C"), speakers.values.map { it.pseudonym }.toSet())
            val hostMembershipId = context.dsl.select(MEMBERSHIP.ID).from(MEMBERSHIP)
                .where(MEMBERSHIP.MEETING_ID.eq(created.meetingId))
                .fetchOne(MEMBERSHIP.ID)
            assertEquals(hostMembershipId, speakers.getValue("Minji").membershipId)
            assertEquals(null, speakers.getValue("Junho").membershipId)

            val view = context.chatImportService.getImport(created.importId)
            assertNotNull(view.purgeAt)
            assertEquals("kakao_export", view.sourceType)
            assertEquals(3, context.dsl.fetchCount(MESSAGE, MESSAGE.IMPORT_ID.eq(created.importId)))
            assertEquals(1, context.dsl.fetchCount(OUTBOX_EVENT, OUTBOX_EVENT.EVENT_TYPE.eq("ChatImportSubmitted")))

            val listed = context.messageService.listMessages(created.threadId)
            assertEquals(listOf("Let's go with the 18th", "ㅇㅇ", "Aewol for lodging?"), listed.map { it.body })
            assertEquals(meeting.ownerUserId, listed.first().speaker.userId)
            assertEquals("Junho", listed[1].speaker.displayName)
        }
    }

    @Test
    fun `meeting chat import skips messages already imported into the same room`() {
        testContext().use { context ->
            val first = context.chatImportService.createImport(
                chatImportRequest(messages = listOf("me" to "Book the ferry", "junho" to "I'll do it")),
            )

            val second = context.chatImportService.createMeetingImport(
                first.meetingId,
                chatImportRequest(
                    messages = listOf("me" to "Book the ferry", "junho" to "I'll do it", "seoyeon" to "Thanks!"),
                ),
            )

            assertEquals(first.threadId, second.threadId)
            assertEquals(1, second.messageCount)
            assertEquals(2, second.duplicateCount)
            assertEquals(null, context.chatImportService.getImport(second.importId).purgeAt)
            assertEquals(3, context.dsl.fetchCount(IMPORT_SPEAKER, IMPORT_SPEAKER.THREAD_ID.eq(first.threadId)))
            assertEquals(2, context.chatImportService.listImports(first.meetingId).size)

            val conflict = assertThrows(ResponseStatusException::class.java) {
                context.chatImportService.createMeetingImport(
                    first.meetingId,
                    chatImportRequest(messages = listOf("me" to "Book the ferry")),
                )
            }
            assertEquals(409, conflict.statusCode.value())
        }
    }

    @Test
    fun `chat import rejects invalid payloads and non host importers`() {
        testContext().use { context ->
            val invalidRequests = listOf(
                chatImportRequest().copy(consent = ChatImportConsentInput(isParticipant = false, version = "v1")),
                chatImportRequest().copy(source = ChatImportSourceInput(type = "discord", format = "bot")),
                chatImportRequest().copy(range = ChatImportRangeInput(IMPORT_START, IMPORT_START.plusDays(91))),
                chatImportRequest().copy(
                    messages = listOf(ChatImportMessageInput("me", IMPORT_START.minusDays(1), "too early")),
                ),
                chatImportRequest().copy(messages = listOf(ChatImportMessageInput("ghost", IMPORT_START, "who?"))),
                chatImportRequest().copy(
                    messages = List(3_001) { ChatImportMessageInput("me", IMPORT_START.plusMinutes(it.toLong() % 60), "m$it") },
                ),
            )
            invalidRequests.forEach { request ->
                val error = assertThrows(ResponseStatusException::class.java) {
                    context.chatImportService.createImport(request)
                }
                assertEquals(400, error.statusCode.value(), error.reason)
            }

            val created = context.chatImportService.createImport(chatImportRequest())
            val participantId = context.addParticipant(created.meetingId, "Participant User")
            val forbidden = withActor(participantId, "Participant User") {
                assertThrows(ResponseStatusException::class.java) {
                    context.chatImportService.createMeetingImport(created.meetingId, chatImportRequest())
                }
            }
            assertEquals(403, forbidden.statusCode.value())
        }
    }

    @Test
    fun `chat import request deserializes boolean flags from json`() {
        val request = ObjectMapper().findAndRegisterModules().readValue(
            """
            {
              "source": { "type": "paste", "format": "free_text" },
              "range": { "start": "2026-10-01T00:00:00+09:00", "end": "2026-10-02T00:00:00+09:00" },
              "speakers": [{ "key": "s1", "displayName": "Minji", "isSelf": true }],
              "messages": [{ "speakerKey": "s1", "sentAt": "2026-10-01T21:14:00+09:00", "body": "hi" }],
              "consent": { "isParticipant": true, "version": "chat-import-consent-v1" }
            }
            """.trimIndent(),
            CreateChatImportRequest::class.java,
        )

        assertEquals(true, request.speakers.single().isSelf)
        assertTrue(request.consent.isParticipant)
        assertEquals(null, request.stats)
    }

    @Test
    fun `chat import analysis stages decisions questions and tasks with evidence`() {
        testContext().use { context ->
            val created = context.chatImportService.createImport(
                chatImportRequest(
                    messages = listOf(
                        "me" to "18일 어때?",
                        "junho" to "ㅇㅇ 좋아",
                        "seoyeon" to "콜",
                        "me" to "그럼 18일로 확정!",
                        "seoyeon" to "배편은 제가 예약할게요",
                        "junho" to "숙소는 어디로 하지?",
                    ),
                ),
            )

            assertEquals(1, context.analysisWorker().analyzePendingBatch())
            assertEquals(0, context.analysisWorker().analyzePendingBatch())
            assertEquals(1.0, context.meterRegistry.counter("dudecide.import.analysis", "outcome", "ready").count())
            assertEquals(1.0, context.meterRegistry.counter("dudecide.import.items", "kind", "decision", "status", "likely_decided").count())

            val view = context.chatImportService.getImport(created.importId)
            assertEquals("ready", view.status)
            assertEquals("done", view.progress["stage"])
            assertNotNull(view.completedAt)

            @Suppress("UNCHECKED_CAST")
            val items = requireNotNull(view.result)["items"] as List<Map<String, Any?>>
            assertEquals(listOf("decision", "open_question", "task"), items.map { it["kind"] })

            val date = items.first()
            assertEquals("likely_decided", date["status"])
            assertEquals("date", date["decisionType"])
            assertEquals(mapOf("option" to "18일"), date["proposedResult"])
            @Suppress("UNCHECKED_CAST")
            val evidenceIds = (date["evidence"] as List<Map<String, Any?>>).map { UUID.fromString(it["messageId"] as String) }
            assertEquals(4, evidenceIds.size)
            assertEquals(4, context.dsl.fetchCount(MESSAGE, MESSAGE.ID.`in`(evidenceIds).and(MESSAGE.IMPORT_ID.eq(created.importId))))

            val seoyeonId = context.dsl.select(IMPORT_SPEAKER.ID).from(IMPORT_SPEAKER)
                .where(IMPORT_SPEAKER.DISPLAY_NAME.eq("Seoyeon"))
                .fetchOne(IMPORT_SPEAKER.ID)
            assertEquals(seoyeonId.toString(), items.last()["ownerSpeakerId"])
            assertEquals("숙소는 어디로 하지?", items[1]["title"])

            val evidenceMessages = context.chatImportService.getEvidence(created.importId)
            assertEquals(6, evidenceMessages.size)
            assertEquals("18일 어때?", evidenceMessages.first().body)

            val record = context.dsl.selectFrom(CHAT_IMPORT).where(CHAT_IMPORT.ID.eq(created.importId)).fetchOne()!!
            assertEquals("chat-import-v1", record.promptVersion)
            assertTrue(record.modelVersions!!.data().contains("fake-map-v1"))
        }
    }

    @Test
    fun `chat import analysis marks the import failed when the map stage fails`() {
        testContext().use { context ->
            val created = context.chatImportService.createImport(chatImportRequest())
            val failing = object : LlmClient {
                override val modelVersions = mapOf("map" to "broken")
                override fun mapEpisode(request: EpisodeMapRequest): EpisodeMapResult = error("provider unavailable")
                override fun reduceSubjects(request: SubjectReduceRequest): SubjectReduceResult = error("unused")
            }

            assertEquals(1, context.analysisWorker(failing).analyzePendingBatch())

            val record = context.dsl.selectFrom(CHAT_IMPORT).where(CHAT_IMPORT.ID.eq(created.importId)).fetchOne()!!
            assertEquals(ChatImportStatus.failed, record.status)
            assertTrue(record.failureReason!!.contains("map stage failed"), record.failureReason)
            assertEquals(null, record.result)
        }
    }

    @Test
    fun `applying staged items drafts decisions and tasks without confirming them`() {
        testContext().use { context ->
            val created = context.analyzedKoreanImport()

            val rejected = assertThrows(ResponseStatusException::class.java) {
                context.chatImportApplyService.apply(created.importId, ApplyChatImportRequest(listOf("it_2")))
            }
            assertEquals(400, rejected.statusCode.value())

            val applied = context.chatImportApplyService.apply(created.importId, ApplyChatImportRequest(listOf("it_1", "it_3")))
            assertEquals(listOf("it_1", "it_3"), applied.appliedItemIds)
            assertEquals("active", applied.meetingStatus)

            val decision = context.dsl.selectFrom(DECISION).where(DECISION.ID.eq(applied.decisionIds.single())).fetchOne()!!
            assertEquals(DecisionStatus.proposed, decision.status)
            assertEquals(context.findThreadId(created.meetingId, ThreadCategory.date), decision.threadId)
            assertTrue(decision.proposedResult!!.data().contains("18일"))
            val quotes = context.dsl.select(DECISION_EVIDENCE.QUOTE_SNAPSHOT, DECISION_EVIDENCE.THREAD_ID)
                .from(DECISION_EVIDENCE)
                .where(DECISION_EVIDENCE.DECISION_ID.eq(decision.id))
                .fetch()
            assertEquals(4, quotes.size)
            assertTrue(quotes.all { it.value2() == created.threadId })
            assertTrue(quotes.any { it.value1()!!.startsWith("Participant B: ") }, quotes.map { it.value1() }.toString())
            assertTrue(quotes.none { it.value1()!!.contains("Junho") })

            val task = context.dsl.selectFrom(TASK_ITEM).where(TASK_ITEM.ID.eq(applied.taskIds.single())).fetchOne()!!
            assertEquals(null, task.ownerUserId)
            assertNotNull(task.sourceMessageId)

            val replay = context.chatImportApplyService.apply(created.importId, ApplyChatImportRequest(listOf("it_1")))
            assertEquals(listOf("it_1"), replay.skippedItemIds)
            assertEquals(emptyList<UUID>(), replay.decisionIds)

            val view = context.chatImportService.getImport(created.importId)
            assertEquals("applied", view.status)
            assertEquals(null, view.purgeAt)
            assertTrue(context.dsl.fetchCount(MEETING_REPORT, MEETING_REPORT.MEETING_ID.eq(created.meetingId)) >= 1)
        }
    }

    @Test
    fun `deleting an import removes its messages and evidence after an impact preview`() {
        testContext().use { context ->
            val created = context.analyzedKoreanImport()
            val applied = context.chatImportApplyService.apply(created.importId, ApplyChatImportRequest(listOf("it_1", "it_3")))
            val participantId = context.addParticipant(created.meetingId, "Participant User")

            val impact = requireNotNull(context.chatImportService.getImport(created.importId, includeDeletionImpact = true).deletionImpact)
            assertEquals(6, impact.removedMessages)
            assertEquals(4, impact.removedEvidence)
            assertEquals(0, impact.affectedConfirmedDecisions)

            val forbidden = withActor(participantId, "Participant User") {
                assertThrows(ResponseStatusException::class.java) { context.chatImportService.deleteImport(created.importId) }
            }
            assertEquals(403, forbidden.statusCode.value())

            val deleted = context.chatImportService.deleteImport(created.importId)
            assertEquals(6, deleted.removedMessages)
            assertEquals(4, deleted.removedEvidence)
            assertEquals(0, context.dsl.fetchCount(MESSAGE, MESSAGE.IMPORT_ID.eq(created.importId)))
            assertEquals(0, context.dsl.fetchCount(DECISION_EVIDENCE, DECISION_EVIDENCE.DECISION_ID.eq(applied.decisionIds.single())))
            assertEquals(1, context.dsl.fetchCount(DECISION, DECISION.ID.eq(applied.decisionIds.single())))
            assertEquals(null, context.dsl.select(TASK_ITEM.SOURCE_MESSAGE_ID).from(TASK_ITEM).where(TASK_ITEM.ID.eq(applied.taskIds.single())).fetchOne(TASK_ITEM.SOURCE_MESSAGE_ID))

            val view = context.chatImportService.getImport(created.importId)
            assertEquals("deleted", view.status)
            assertEquals(null, view.result)
            assertEquals(0, context.chatImportService.deleteImport(created.importId).removedMessages)
        }
    }

    @Test
    fun `deleting during analysis keeps the import deleted`() {
        testContext().use { context ->
            val created = context.chatImportService.createImport(chatImportRequest())
            val analyzer = context.analyzer()

            assertEquals(created.importId, analyzer.claimNext())
            context.chatImportService.deleteImport(created.importId)
            analyzer.analyze(created.importId)

            val record = context.dsl.selectFrom(CHAT_IMPORT).where(CHAT_IMPORT.ID.eq(created.importId)).fetchOne()!!
            assertEquals(ChatImportStatus.deleted, record.status)
            assertEquals(null, record.result)
        }
    }

    @Test
    fun `speakers hide exported names from members and support mapping and claims`() {
        testContext().use { context ->
            val created = context.analyzedKoreanImport()
            val firstId = context.addParticipant(created.meetingId, "Participant User")
            val secondId = context.addParticipant(created.meetingId, "Second User")

            val hostView = context.importSpeakerService.listSpeakers(created.meetingId)
            assertEquals(setOf("Minji", "Junho", "Seoyeon"), hostView.map { it.displayName }.toSet())

            val memberView = withActor(firstId, "Participant User") { context.importSpeakerService.listSpeakers(created.meetingId) }
            assertTrue(memberView.all { it.displayName == null && it.pseudonym == null })
            assertEquals(setOf("Local Host", "Participant B", "Participant C"), memberView.map { it.label }.toSet())

            val claimed = withActor(firstId, "Participant User") {
                context.importSpeakerService.claimSpeaker(created.meetingId, ClaimImportSpeakerRequest("  junho "))
            }
            val afterClaim = withActor(firstId, "Participant User") { context.importSpeakerService.listSpeakers(created.meetingId) }
            assertEquals(listOf("Participant User"), afterClaim.filter { it.mine }.map { it.label })
            val junho = hostView.single { it.displayName == "Junho" }
            assertEquals(listOf(junho.id), claimed.claimedSpeakerIds)
            val conflict = withActor(firstId, "Participant User") {
                assertThrows(ResponseStatusException::class.java) {
                    context.importSpeakerService.claimSpeaker(created.meetingId, ClaimImportSpeakerRequest("Seoyeon"))
                }
            }
            assertEquals(409, conflict.statusCode.value())
            val missing = withActor(secondId, "Second User") {
                assertThrows(ResponseStatusException::class.java) {
                    context.importSpeakerService.claimSpeaker(created.meetingId, ClaimImportSpeakerRequest("Nobody"))
                }
            }
            assertEquals(404, missing.statusCode.value())

            val labels = withActor(secondId, "Second User") { context.messageService.listMessages(created.threadId) }
                .map { it.speaker.displayName }
            assertTrue("Participant User" in labels)

            val seoyeon = hostView.single { it.displayName == "Seoyeon" }
            val secondMembership = context.dsl.select(MEMBERSHIP.ID).from(MEMBERSHIP).where(MEMBERSHIP.USER_ID.eq(secondId)).fetchOne(MEMBERSHIP.ID)
            val mapped = context.importSpeakerService.updateSpeaker(seoyeon.id, UpdateImportSpeakerRequest(secondMembership))
            assertEquals("Second User", mapped.label)
            assertTrue(mapped.mapped)

            val invalid = assertThrows(ResponseStatusException::class.java) {
                context.importSpeakerService.updateSpeaker(seoyeon.id, UpdateImportSpeakerRequest(UUID.randomUUID()))
            }
            assertEquals(400, invalid.statusCode.value())
            val forbidden = withActor(firstId, "Participant User") {
                assertThrows(ResponseStatusException::class.java) {
                    context.importSpeakerService.updateSpeaker(seoyeon.id, UpdateImportSpeakerRequest(null))
                }
            }
            assertEquals(403, forbidden.statusCode.value())
        }
    }

    @Test
    fun `invite preview shows titles and counts without quotes or speaker names`() {
        testContext().use { context ->
            val created = context.analyzedKoreanImport()
            context.chatImportApplyService.apply(created.importId, ApplyChatImportRequest(listOf("it_1", "it_3")))
            val invite = context.meetingService.createInviteLink(created.meetingId, CreateInviteLinkRequest())

            val preview = context.invitePreviewService.preview(invite.token)

            assertEquals("Jeju trip", preview.meetingTitle)
            assertEquals(listOf("Date"), preview.decisions.map { it.title })
            assertEquals(1, preview.pendingDecisionCount)
            assertEquals(0, preview.confirmedDecisionCount)
            assertEquals(1, preview.openTaskCount)
            assertEquals(1, preview.openQuestionCount)
            val json = context.objectMapper.writeValueAsString(preview)
            listOf("Minji", "Junho", "Seoyeon", "Participant", "어때", "숙소는").forEach {
                assertTrue(it !in json, "preview leaked: $it")
            }

            val missing = assertThrows(ResponseStatusException::class.java) { context.invitePreviewService.preview("nope") }
            assertEquals(404, missing.statusCode.value())
            val expired = context.meetingService.createInviteLink(
                created.meetingId,
                CreateInviteLinkRequest(expiresAt = OffsetDateTime.now().minusMinutes(1)),
            )
            val gone = assertThrows(ResponseStatusException::class.java) { context.invitePreviewService.preview(expired.token) }
            assertEquals(410, gone.statusCode.value())
        }
    }

    @Test
    fun `chat imports are limited per person and per meeting over 24 hours`() {
        testContext().use { context ->
            repeat(3) { context.chatImportService.createImport(chatImportRequest(messages = listOf("me" to "memo $it"))) }

            val perActor = assertThrows(ResponseStatusException::class.java) {
                context.chatImportService.createImport(chatImportRequest())
            }
            assertEquals(429, perActor.statusCode.value())

            val generous = context.chatImportServiceFactory(ChatImportLimitProperties(dailyPerActor = 100, dailyPerMeeting = 2))
            val first = generous.createImport(chatImportRequest(messages = listOf("me" to "first")))
            generous.createMeetingImport(first.meetingId, chatImportRequest(messages = listOf("me" to "second")))
            val perMeeting = assertThrows(ResponseStatusException::class.java) {
                generous.createMeetingImport(first.meetingId, chatImportRequest(messages = listOf("me" to "third")))
            }
            assertEquals(429, perMeeting.statusCode.value())
            assertTrue(perMeeting.reason!!.contains("per meeting"))
        }
    }

    @Test
    fun `purge removes expired draft meetings but keeps applied ones`() {
        testContext().use { context ->
            val expired = context.chatImportService.createImport(chatImportRequest(messages = listOf("me" to "old")))
            val applied = context.analyzedKoreanImport()
            context.chatImportApplyService.apply(applied.importId, ApplyChatImportRequest(listOf("it_1")))
            context.dsl.update(CHAT_IMPORT)
                .set(CHAT_IMPORT.PURGE_AT, OffsetDateTime.now().minusMinutes(1))
                .execute()

            assertEquals(1, context.maintenanceWorker().purgeExpired(OffsetDateTime.now()))

            assertEquals(0, context.dsl.fetchCount(MEETING, MEETING.ID.eq(expired.meetingId)))
            assertEquals(0, context.dsl.fetchCount(MESSAGE, MESSAGE.IMPORT_ID.eq(expired.importId)))
            assertEquals(1, context.dsl.fetchCount(MEETING, MEETING.ID.eq(applied.meetingId)))
            assertEquals(1.0, context.meterRegistry.counter("dudecide.import.purged.meetings").count())
        }
    }

    @Test
    fun `stale analysis claims are requeued once and then failed`() {
        testContext().use { context ->
            val created = context.chatImportService.createImport(chatImportRequest())
            val analyzer = context.analyzer()
            val stale = { context.dsl.update(CHAT_IMPORT).set(CHAT_IMPORT.CLAIMED_AT, OffsetDateTime.now().minusMinutes(20)).execute() }

            assertEquals(created.importId, analyzer.claimNext())
            stale()
            assertEquals(ChatImportMaintenanceWorker.ReclaimResult(requeued = 1, failed = 0), context.maintenanceWorker().reclaimStale(OffsetDateTime.now()))
            assertEquals(ChatImportStatus.submitted, context.dsl.select(CHAT_IMPORT.STATUS).from(CHAT_IMPORT).fetchOne(CHAT_IMPORT.STATUS))

            assertEquals(created.importId, analyzer.claimNext())
            stale()
            assertEquals(ChatImportMaintenanceWorker.ReclaimResult(requeued = 0, failed = 1), context.maintenanceWorker().reclaimStale(OffsetDateTime.now()))
            val record = context.dsl.selectFrom(CHAT_IMPORT).fetchOne()!!
            assertEquals(ChatImportStatus.failed, record.status)
            assertEquals(2, record.attempts)
        }
    }

    private fun TestContext.analyzedKoreanImport() =
        chatImportService.createImport(
            chatImportRequest(
                messages = listOf(
                    "me" to "18일 어때?",
                    "junho" to "ㅇㅇ 좋아",
                    "seoyeon" to "콜",
                    "me" to "그럼 18일로 확정!",
                    "seoyeon" to "배편은 제가 예약할게요",
                    "junho" to "숙소는 어디로 하지?",
                ),
            ),
        ).also { analysisWorker().analyzePendingBatch() }

    private fun chatImportRequest(
        messages: List<Pair<String, String>> = listOf("me" to "Let's go with the 18th"),
    ): CreateChatImportRequest =
        CreateChatImportRequest(
            source = ChatImportSourceInput(type = "kakao_export", format = "kakao_android", roomName = "Jeju trip"),
            range = ChatImportRangeInput(start = IMPORT_START, end = IMPORT_START.plusDays(1)),
            speakers = listOf(
                ChatImportSpeakerInput(key = "me", displayName = "Minji", isSelf = true),
                ChatImportSpeakerInput(key = "junho", displayName = "Junho"),
                ChatImportSpeakerInput(key = "seoyeon", displayName = "Seoyeon"),
            ),
            messages = messages.mapIndexed { index, (speakerKey, body) ->
                ChatImportMessageInput(speakerKey, IMPORT_START.plusMinutes(index.toLong()), body)
            },
            consent = ChatImportConsentInput(isParticipant = true, version = "chat-import-consent-v1"),
        )

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

        val meetingService = MeetingService(dsl, localActorProvider, meetingAccessService, outboxService, eventStreamService, notificationService)
        val messageService = MessageService(dsl, objectMapper, localActorProvider, meetingAccessService, outboxService, eventStreamService)
        val taskService = TaskService(dsl, meetingAccessService, outboxService, eventStreamService)
        val reportService = ReportService(dsl, objectMapper, meetingAccessService, outboxService, eventStreamService, notificationService)
        val meterRegistry = SimpleMeterRegistry()
        val chatImportServiceFactory = { limits: ChatImportLimitProperties ->
            ChatImportService(
                dsl,
                objectMapper,
                localActorProvider,
                meetingAccessService,
                meetingService,
                outboxService,
                eventStreamService,
                messageService,
                limits,
            )
        }

        return TestContext(
            dsl = dsl,
            objectMapper = objectMapper,
            meetingService = meetingService,
            voteService = VoteService(dsl, objectMapper, localActorProvider, meetingAccessService, outboxService, eventStreamService, notificationService),
            decisionService = DecisionService(dsl, objectMapper, localActorProvider, meetingAccessService, outboxService, eventStreamService, notificationService),
            messageService = messageService,
            threadService = ThreadService(dsl, meetingAccessService, outboxService, eventStreamService),
            reportService = reportService,
            searchService = SearchService(dsl, meetingAccessService),
            taskService = taskService,
            notificationService = notificationService,
            chatImportService = chatImportServiceFactory(ChatImportLimitProperties()),
            eventStreamService = eventStreamService,
            chatImportApplyService = ChatImportApplyService(
                dsl,
                objectMapper,
                meetingAccessService,
                outboxService,
                eventStreamService,
                taskService,
                reportService,
                meterRegistry,
            ),
            importSpeakerService = ImportSpeakerService(dsl, meetingAccessService, outboxService, eventStreamService),
            invitePreviewService = InvitePreviewService(dsl, objectMapper),
            meterRegistry = meterRegistry,
            chatImportServiceFactory = chatImportServiceFactory,
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
                read_only BOOLEAN NOT NULL DEFAULT FALSE,
                CONSTRAINT topic_thread_meeting_fk FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE
            );

            CREATE TABLE chat_import (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                meeting_id UUID NOT NULL,
                thread_id UUID NOT NULL,
                uploaded_by_user_id UUID NOT NULL,
                source_type VARCHAR(64) NOT NULL,
                source_format VARCHAR(64) NOT NULL,
                source_room_name VARCHAR(200),
                range_start TIMESTAMP WITH TIME ZONE NOT NULL,
                range_end TIMESTAMP WITH TIME ZONE NOT NULL,
                message_count INTEGER NOT NULL,
                duplicate_count INTEGER NOT NULL DEFAULT 0,
                status VARCHAR(64) NOT NULL DEFAULT 'submitted',
                progress JSON NOT NULL DEFAULT JSON '{}',
                result JSON,
                stats JSON NOT NULL DEFAULT JSON '{}',
                consent_version VARCHAR(64) NOT NULL,
                prompt_version VARCHAR(64),
                model_versions JSON NOT NULL DEFAULT JSON '{}',
                failure_reason VARCHAR(200),
                purge_at TIMESTAMP WITH TIME ZONE,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                completed_at TIMESTAMP WITH TIME ZONE,
                claimed_at TIMESTAMP WITH TIME ZONE,
                attempts INTEGER NOT NULL DEFAULT 0,
                CONSTRAINT chat_import_meeting_fk FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
                CONSTRAINT chat_import_thread_fk FOREIGN KEY (thread_id) REFERENCES topic_thread(id) ON DELETE CASCADE,
                CONSTRAINT chat_import_uploader_fk FOREIGN KEY (uploaded_by_user_id) REFERENCES app_user(id),
                CONSTRAINT chat_import_range_ck CHECK (range_start <= range_end)
            );

            CREATE TABLE import_speaker (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                meeting_id UUID NOT NULL,
                thread_id UUID NOT NULL,
                display_name VARCHAR(120) NOT NULL,
                pseudonym VARCHAR(32) NOT NULL,
                membership_id UUID,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT import_speaker_meeting_fk FOREIGN KEY (meeting_id) REFERENCES meeting(id) ON DELETE CASCADE,
                CONSTRAINT import_speaker_thread_fk FOREIGN KEY (thread_id) REFERENCES topic_thread(id) ON DELETE CASCADE,
                CONSTRAINT import_speaker_membership_fk FOREIGN KEY (membership_id) REFERENCES membership(id) ON DELETE SET NULL,
                CONSTRAINT import_speaker_thread_name_uk UNIQUE (thread_id, display_name),
                CONSTRAINT import_speaker_thread_pseudonym_uk UNIQUE (thread_id, pseudonym)
            );

            CREATE TABLE message (
                id UUID DEFAULT random_uuid() PRIMARY KEY,
                thread_id UUID NOT NULL,
                sender_user_id UUID,
                body TEXT NOT NULL,
                attachments JSON NOT NULL DEFAULT JSON '[]',
                ai_labels JSON NOT NULL DEFAULT JSON '{}',
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                edited_at TIMESTAMP WITH TIME ZONE,
                origin VARCHAR(64) NOT NULL DEFAULT 'native',
                import_id UUID,
                import_speaker_id UUID,
                content_hash CHAR(64),
                CONSTRAINT message_thread_fk FOREIGN KEY (thread_id) REFERENCES topic_thread(id) ON DELETE CASCADE,
                CONSTRAINT message_sender_fk FOREIGN KEY (sender_user_id) REFERENCES app_user(id),
                CONSTRAINT message_import_fk FOREIGN KEY (import_id) REFERENCES chat_import(id) ON DELETE CASCADE,
                CONSTRAINT message_import_speaker_fk FOREIGN KEY (import_speaker_id) REFERENCES import_speaker(id) ON DELETE CASCADE,
                CONSTRAINT message_sender_origin_ck CHECK (
                    (origin = 'native' AND sender_user_id IS NOT NULL AND import_speaker_id IS NULL)
                    OR (
                        origin = 'imported'
                        AND sender_user_id IS NULL
                        AND import_speaker_id IS NOT NULL
                        AND import_id IS NOT NULL
                        AND content_hash IS NOT NULL
                    )
                ),
                CONSTRAINT message_thread_content_hash_uk UNIQUE (thread_id, content_hash)
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
                read_at TIMESTAMP WITH TIME ZONE,
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

    private companion object {
        val IMPORT_START: OffsetDateTime = OffsetDateTime.parse("2026-10-01T09:00:00+09:00")
    }

    private data class SeededImport(val threadId: UUID, val importId: UUID, val speakerId: UUID)

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
        val chatImportService: ChatImportService,
        val eventStreamService: EventStreamService,
        val chatImportApplyService: ChatImportApplyService,
        val importSpeakerService: ImportSpeakerService,
        val invitePreviewService: InvitePreviewService,
        val meterRegistry: SimpleMeterRegistry,
        val chatImportServiceFactory: (ChatImportLimitProperties) -> ChatImportService,
    ) : AutoCloseable {
        override fun close() = Unit

        fun analyzer(llmClient: LlmClient = FakeLlmClient()): ChatImportAnalyzer =
            ChatImportAnalyzer(dsl, objectMapper, llmClient, eventStreamService, ChatImportAiProperties(), meterRegistry)

        fun maintenanceWorker(): ChatImportMaintenanceWorker = ChatImportMaintenanceWorker(dsl, meterRegistry)

        fun analysisWorker(llmClient: LlmClient = FakeLlmClient()): ChatImportAnalysisWorker =
            ChatImportAnalysisWorker(analyzer(llmClient))

        fun findThreadId(meetingId: UUID, category: ThreadCategory): UUID =
            dsl.select(TOPIC_THREAD.ID)
                .from(TOPIC_THREAD)
                .where(TOPIC_THREAD.MEETING_ID.eq(meetingId))
                .and(TOPIC_THREAD.CATEGORY.eq(category))
                .fetchOne(TOPIC_THREAD.ID)
                ?: error("thread not found for $category")

        fun ownerId(meetingId: UUID): UUID =
            dsl.select(MEETING.OWNER_USER_ID)
                .from(MEETING)
                .where(MEETING.ID.eq(meetingId))
                .fetchOne(MEETING.OWNER_USER_ID)
                ?: error("meeting not found")

        fun seedImport(meetingId: UUID, threadId: UUID, uploaderId: UUID): SeededImport {
            val seeded = SeededImport(threadId = threadId, importId = UUID.randomUUID(), speakerId = UUID.randomUUID())
            val now = OffsetDateTime.now()
            dsl.insertInto(CHAT_IMPORT)
                .set(CHAT_IMPORT.ID, seeded.importId)
                .set(CHAT_IMPORT.MEETING_ID, meetingId)
                .set(CHAT_IMPORT.THREAD_ID, threadId)
                .set(CHAT_IMPORT.UPLOADED_BY_USER_ID, uploaderId)
                .set(CHAT_IMPORT.SOURCE_TYPE, ChatImportSource.kakao_export)
                .set(CHAT_IMPORT.SOURCE_FORMAT, "kakao_android")
                .set(CHAT_IMPORT.RANGE_START, now.minusDays(1))
                .set(CHAT_IMPORT.RANGE_END, now)
                .set(CHAT_IMPORT.MESSAGE_COUNT, 1)
                .set(CHAT_IMPORT.CONSENT_VERSION, "chat-import-consent-v1")
                .execute()
            dsl.insertInto(IMPORT_SPEAKER)
                .set(IMPORT_SPEAKER.ID, seeded.speakerId)
                .set(IMPORT_SPEAKER.MEETING_ID, meetingId)
                .set(IMPORT_SPEAKER.THREAD_ID, threadId)
                .set(IMPORT_SPEAKER.DISPLAY_NAME, "Minji")
                .set(IMPORT_SPEAKER.PSEUDONYM, "Participant A")
                .execute()
            return seeded
        }

        fun insertImportedMessage(seeded: SeededImport, body: String, contentHash: String): UUID {
            val messageId = UUID.randomUUID()
            dsl.insertInto(MESSAGE)
                .set(MESSAGE.ID, messageId)
                .set(MESSAGE.THREAD_ID, seeded.threadId)
                .set(MESSAGE.BODY, body)
                .set(MESSAGE.ORIGIN, MessageOrigin.imported)
                .set(MESSAGE.IMPORT_ID, seeded.importId)
                .set(MESSAGE.IMPORT_SPEAKER_ID, seeded.speakerId)
                .set(MESSAGE.CONTENT_HASH, contentHash)
                .execute()
            return messageId
        }

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
