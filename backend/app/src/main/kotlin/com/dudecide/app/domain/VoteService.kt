package com.dudecide.app.domain

import com.dudecide.app.api.dto.CreateVoteOptionRequest
import com.dudecide.app.api.dto.CreateVoteRequest
import com.dudecide.app.api.dto.VoteAggregateOptionView
import com.dudecide.app.api.dto.VoteAggregateView
import com.dudecide.app.api.dto.VoteOptionView
import com.dudecide.app.api.dto.VoteResponseView
import com.dudecide.app.api.dto.VoteStatusDto
import com.dudecide.app.api.dto.VoteSubmissionItem
import com.dudecide.app.api.dto.VoteSubmissionRequest
import com.dudecide.app.jooq.enums.DecisionStatus
import com.dudecide.app.jooq.enums.DecisionType
import com.dudecide.app.jooq.enums.NotificationType
import com.dudecide.app.jooq.enums.VoteResponseValue
import com.dudecide.app.jooq.enums.VoteStatus
import com.dudecide.app.jooq.enums.VoteType
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.Meeting.Companion.MEETING
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.TopicThread.Companion.TOPIC_THREAD
import com.dudecide.app.jooq.tables.Vote.Companion.VOTE
import com.dudecide.app.jooq.tables.VoteOption.Companion.VOTE_OPTION
import com.dudecide.app.jooq.tables.VoteResponse.Companion.VOTE_RESPONSE
import com.dudecide.app.jooq.tables.records.DecisionRecord
import com.dudecide.app.jooq.tables.records.VoteOptionRecord
import com.dudecide.app.jooq.tables.records.VoteRecord
import com.dudecide.app.jooq.tables.records.VoteResponseRecord
import com.dudecide.shared.vote.DateVoteCounts
import com.dudecide.shared.vote.LocationVoteCounts
import com.dudecide.shared.vote.VoteScoringService
import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.DSLContext
import org.jooq.JSON
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Service
class VoteService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    private val localActorProvider: LocalActorProvider,
    private val meetingAccessService: MeetingAccessService,
    private val outboxService: OutboxService,
    private val eventStreamService: EventStreamService,
    private val notificationService: NotificationService,
) {
    private val voteScoringService = VoteScoringService()

    @Transactional
    fun createVote(meetingId: UUID, request: CreateVoteRequest): VoteResponseView {
        val actorId = meetingAccessService.requireMeetingHostOrCoHost(meetingId)
        requireMeeting(meetingId)
        requireThread(meetingId, request.threadId)

        val now = OffsetDateTime.now()
        validateCreateRequest(request, now)
        val voteType = request.voteType.toVoteType()
        val decisionId = ensureDecision(meetingId, request, voteType)
        val voteId = UUID.randomUUID()
        val status = if (request.opensAt == null || !request.opensAt.isAfter(now)) VoteStatus.open else VoteStatus.draft

        dsl.insertInto(VOTE)
            .set(VOTE.ID, voteId)
            .set(VOTE.MEETING_ID, meetingId)
            .set(VOTE.DECISION_ID, decisionId)
            .set(VOTE.THREAD_ID, request.threadId)
            .set(VOTE.VOTE_TYPE, voteType)
            .set(VOTE.STATUS, status)
            .set(VOTE.SCORING_RULE, jsonOf(defaultScoringRule(voteType)))
            .set(VOTE.OPENS_AT, request.opensAt)
            .set(VOTE.DEADLINE_AT, request.deadlineAt)
            .execute()

        request.options.forEachIndexed { index, option ->
            dsl.insertInto(VOTE_OPTION)
                .set(VOTE_OPTION.ID, UUID.randomUUID())
                .set(VOTE_OPTION.VOTE_ID, voteId)
                .set(VOTE_OPTION.OPTION_TYPE, option.optionType)
                .set(VOTE_OPTION.LABEL, option.label)
                .set(VOTE_OPTION.PAYLOAD, jsonOf(option.payload))
                .set(VOTE_OPTION.SORT_ORDER, option.sortOrder ?: index)
                .execute()
        }

        val vote = getRequiredVote(voteId)
        val options = getVoteOptions(voteId)
        val response = buildVoteResponse(vote, options, actorId)
        updateLinkedDecisionSnapshot(vote, response, closedByHost = false)
        outboxService.append(
            aggregateType = "vote",
            aggregateId = voteId,
            eventType = "VoteCreated",
            payload = mapOf(
                "voteId" to voteId,
                "meetingId" to meetingId,
                "decisionId" to decisionId,
                "threadId" to request.threadId,
                "voteType" to voteType.literal,
            ),
        )
        eventStreamService.publish(
            meetingId = meetingId,
            eventType = "vote.updated",
            payload = mapOf(
                "meetingId" to meetingId,
                "voteId" to voteId,
                "status" to status.literal,
            ),
        )
        return response
    }

    @Transactional(readOnly = true)
    fun getVote(voteId: UUID): VoteResponseView {
        val actorId = meetingAccessService.requireVoteMember(voteId)
        val vote = getRequiredVote(voteId)
        val options = getVoteOptions(voteId)
        return buildVoteResponse(vote, options, actorId)
    }

    @Transactional
    fun submitResponses(voteId: UUID, request: VoteSubmissionRequest): VoteResponseView {
        val actorId = meetingAccessService.requireVoteMember(voteId)
        val vote = getRequiredVote(voteId)

        if (vote.status != VoteStatus.open) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "vote is not open")
        }

        val options = getVoteOptions(voteId)
        validateSubmission(vote, options, request)

        dsl.deleteFrom(VOTE_RESPONSE)
            .where(VOTE_RESPONSE.VOTE_ID.eq(voteId))
            .and(VOTE_RESPONSE.USER_ID.eq(actorId))
            .execute()

        request.responses.forEach { item ->
            dsl.insertInto(VOTE_RESPONSE)
                .set(VOTE_RESPONSE.ID, UUID.randomUUID())
                .set(VOTE_RESPONSE.VOTE_ID, voteId)
                .set(VOTE_RESPONSE.OPTION_ID, item.optionId)
                .set(VOTE_RESPONSE.USER_ID, actorId)
                .set(VOTE_RESPONSE.RESPONSE_VALUE, item.responseValue.toVoteResponseValue())
                .set(VOTE_RESPONSE.RESPONSE_PAYLOAD, JSON.valueOf("{}"))
                .execute()
        }

        val response = buildVoteResponse(vote, options, actorId)
        updateLinkedDecisionSnapshot(vote, response, closedByHost = false)
        outboxService.append(
            aggregateType = "vote",
            aggregateId = voteId,
            eventType = "VoteResponseUpserted",
            payload = mapOf(
                "voteId" to voteId,
                "meetingId" to requireNotNull(vote.meetingId),
                "decisionId" to vote.decisionId,
                "threadId" to requireNotNull(vote.threadId),
                "actorUserId" to actorId,
                "responseCount" to request.responses.size,
            ),
        )
        eventStreamService.publish(
            meetingId = requireNotNull(vote.meetingId),
            eventType = "vote.updated",
            payload = mapOf(
                "meetingId" to requireNotNull(vote.meetingId),
                "voteId" to voteId,
                "kind" to "responses.updated",
            ),
        )
        return response
    }

    @Transactional
    fun closeVote(voteId: UUID): VoteResponseView {
        val actorId = meetingAccessService.requireVoteHostOrCoHost(voteId)
        val vote = getRequiredVote(voteId)

        if (vote.status == VoteStatus.closed || vote.status == VoteStatus.superseded) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "vote is already closed")
        }

        val closedAt = OffsetDateTime.now()
        dsl.update(VOTE)
            .set(VOTE.STATUS, VoteStatus.closed)
            .set(VOTE.CLOSED_AT, closedAt)
            .where(VOTE.ID.eq(voteId))
            .execute()

        val closedVote = getRequiredVote(voteId)
        val options = getVoteOptions(voteId)
        val response = buildVoteResponse(closedVote, options, actorId)
        updateLinkedDecisionSnapshot(closedVote, response, closedByHost = true)
        outboxService.append(
            aggregateType = "vote",
            aggregateId = voteId,
            eventType = "VoteClosed",
            payload = mapOf(
                "voteId" to voteId,
                "meetingId" to requireNotNull(closedVote.meetingId),
                "decisionId" to closedVote.decisionId,
                "threadId" to requireNotNull(closedVote.threadId),
                "closedAt" to closedAt.toString(),
                "leadingOptionIds" to response.aggregates?.leadingOptionIds.orEmpty(),
            ),
        )
        eventStreamService.publish(
            meetingId = requireNotNull(closedVote.meetingId),
            eventType = "vote.updated",
            payload = mapOf(
                "meetingId" to requireNotNull(closedVote.meetingId),
                "voteId" to voteId,
                "status" to "closed",
            ),
        )
        notificationService.notifyMembers(
            meetingId = requireNotNull(closedVote.meetingId),
            excludeUserId = actorId,
            type = NotificationType.vote_closed,
            payload = mapOf(
                "voteId" to voteId,
                "leadingOptionIds" to response.aggregates?.leadingOptionIds.orEmpty(),
            ),
        )
        return response
    }

    private fun validateCreateRequest(request: CreateVoteRequest, now: OffsetDateTime) {
        if (request.options.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one vote option is required")
        }

        if (request.deadlineAt != null && request.opensAt != null && request.deadlineAt.isBefore(request.opensAt)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "deadlineAt must be after opensAt")
        }

        if (request.deadlineAt != null && request.deadlineAt.isBefore(now)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "deadlineAt cannot be in the past")
        }

        val duplicateLabels = request.options.groupBy { it.label.trim() }.filterValues { it.size > 1 }.keys
        if (duplicateLabels.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate vote option labels are not allowed")
        }
    }

    private fun ensureDecision(meetingId: UUID, request: CreateVoteRequest, voteType: VoteType): UUID {
        request.decisionId?.let { decisionId ->
            val existing = dsl.selectFrom(DECISION)
                .where(DECISION.ID.eq(decisionId))
                .and(DECISION.MEETING_ID.eq(meetingId))
                .and(DECISION.THREAD_ID.eq(request.threadId))
                .fetchOne()
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "decision does not belong to the meeting thread")

            if (existing.status == DecisionStatus.confirmed) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "confirmed decision cannot be linked to a new vote")
            }

            return decisionId
        }

        val decisionType = request.decisionType?.toDecisionType() ?: defaultDecisionType(voteType)
        val decisionId = UUID.randomUUID()

        dsl.insertInto(DECISION)
            .set(DECISION.ID, decisionId)
            .set(DECISION.MEETING_ID, meetingId)
            .set(DECISION.THREAD_ID, request.threadId)
            .set(DECISION.DECISION_TYPE, decisionType)
            .set(DECISION.TITLE, request.title)
            .set(DECISION.STATUS, DecisionStatus.voting)
            .set(DECISION.PROPOSED_RESULT, JSON.valueOf("{}"))
            .set(DECISION.CONFIRMED_RESULT, JSON.valueOf("{}"))
            .execute()

        return decisionId
    }

    private fun validateSubmission(
        vote: VoteRecord,
        options: List<VoteOptionRecord>,
        request: VoteSubmissionRequest,
    ) {
        val duplicateOptionIds = request.responses.groupBy { it.optionId }.filterValues { it.size > 1 }.keys
        if (duplicateOptionIds.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate option ids are not allowed")
        }

        val validOptionIds = options.mapNotNull { it.id }.toSet()
        if (!request.responses.all { it.optionId in validOptionIds }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "request contains unknown option ids")
        }

        val allowedValues = allowedResponseValues(requireNotNull(vote.voteType))
        if (!request.responses.all { it.responseValue in allowedValues }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "request contains invalid response values for vote type")
        }
    }

    private fun buildVoteResponse(
        vote: VoteRecord,
        options: List<VoteOptionRecord>,
        actorId: UUID,
    ): VoteResponseView {
        val voteId = requireNotNull(vote.id)
        val responses = dsl.selectFrom(VOTE_RESPONSE)
            .where(VOTE_RESPONSE.VOTE_ID.eq(voteId))
            .fetch()

        val optionViews = options.map { option ->
            VoteOptionView(
                id = requireNotNull(option.id),
                optionType = requireNotNull(option.optionType),
                label = requireNotNull(option.label),
                payload = jsonToMap(option.payload),
            )
        }

        val currentUserResponses = responses
            .filter { it.userId == actorId }
            .sortedBy { response -> options.indexOfFirst { it.id == response.optionId } }
            .map { response ->
                VoteSubmissionItem(
                    optionId = requireNotNull(response.optionId),
                    responseValue = requireNotNull(response.responseValue).literal,
                )
            }

        return VoteResponseView(
            voteId = voteId,
            meetingId = requireNotNull(vote.meetingId),
            decisionId = vote.decisionId,
            threadId = requireNotNull(vote.threadId),
            voteType = requireNotNull(vote.voteType).literal,
            status = requireNotNull(vote.status).toDto(),
            opensAt = vote.opensAt,
            deadlineAt = vote.deadlineAt,
            closedAt = vote.closedAt,
            options = optionViews,
            responses = currentUserResponses,
            aggregates = buildAggregates(vote, optionViews, responses),
        )
    }

    private fun buildAggregates(
        vote: VoteRecord,
        options: List<VoteOptionView>,
        responses: List<VoteResponseRecord>,
    ): VoteAggregateView {
        val meetingId = requireNotNull(vote.meetingId)
        val totalParticipants = dsl.fetchCount(MEMBERSHIP, MEMBERSHIP.MEETING_ID.eq(meetingId))
        val respondedParticipants = responses.mapNotNull { it.userId }.toSet().size
        val unresolvedParticipants = (totalParticipants - respondedParticipants).coerceAtLeast(0)
        val voteType = requireNotNull(vote.voteType)

        return when (voteType) {
            VoteType.date_matrix -> buildDateAggregates(options, responses, totalParticipants, respondedParticipants, unresolvedParticipants)
            VoteType.package_preference -> buildLocationAggregates(options, responses, totalParticipants, respondedParticipants, unresolvedParticipants)
            VoteType.binary_confirm -> buildBinaryAggregates(options, responses, totalParticipants, respondedParticipants, unresolvedParticipants)
        }
    }

    private fun buildDateAggregates(
        options: List<VoteOptionView>,
        responses: List<VoteResponseRecord>,
        totalParticipants: Int,
        respondedParticipants: Int,
        unresolvedParticipants: Int,
    ): VoteAggregateView {
        val countsByOption = buildCountsByOption(options, responses)
        val scoredCounts = options.map { option ->
            val counts = countsByOption.getValue(option.id)
            DateVoteCounts(
                date = option.extractDate(),
                available = counts["available"] ?: 0,
                maybe = counts["maybe"] ?: 0,
                unavailable = counts["unavailable"] ?: 0,
            )
        }
        val ranked = voteScoringService.scoreDates(scoredCounts)
        val rankedByDate = ranked.associateBy { it.date }
        val rankedOptionViews = options
            .sortedWith(compareBy<VoteOptionView> { rankedByDate.getValue(it.extractDate()).unavailable }
                .thenByDescending { rankedByDate.getValue(it.extractDate()).rawScore }
                .thenByDescending { rankedByDate.getValue(it.extractDate()).available }
                .thenByDescending { rankedByDate.getValue(it.extractDate()).maybe }
                .thenBy { it.extractDate() })
            .mapIndexed { index, option ->
                val score = rankedByDate.getValue(option.extractDate())
                VoteAggregateOptionView(
                    optionId = option.id,
                    label = option.label,
                    counts = countsByOption.getValue(option.id),
                    rawScore = score.rawScore,
                    rank = index + 1,
                )
            }

        return VoteAggregateView(
            totalParticipants = totalParticipants,
            respondedParticipants = respondedParticipants,
            unresolvedParticipants = unresolvedParticipants,
            canEarlyClose = if (rankedOptionViews.isNotEmpty()) voteScoringService.canEarlyCloseDateVote(scoredCounts, unresolvedParticipants) else null,
            leadingOptionIds = rankedOptionViews.takeWhile { it.rank == 1 }.map { it.optionId }.ifEmpty {
                rankedOptionViews.firstOrNull()?.let { listOf(it.optionId) } ?: emptyList()
            },
            options = rankedOptionViews,
        )
    }

    private fun buildLocationAggregates(
        options: List<VoteOptionView>,
        responses: List<VoteResponseRecord>,
        totalParticipants: Int,
        respondedParticipants: Int,
        unresolvedParticipants: Int,
    ): VoteAggregateView {
        val countsByOption = buildCountsByOption(options, responses)
        val scoredCounts = options.map { option ->
            val counts = countsByOption.getValue(option.id)
            val payload = option.payload
            LocationVoteCounts(
                optionId = option.id.toString(),
                good = counts["good"] ?: 0,
                okay = counts["okay"] ?: 0,
                dislike = counts["dislike"] ?: 0,
                maxBudget = payload["maxBudget"].toIntValue(),
                travelMinutes = payload["travelMinutes"].toIntValue(),
            )
        }
        val ranked = voteScoringService.scoreLocations(scoredCounts)
        val rankedByOptionId = ranked.associateBy { UUID.fromString(it.optionId) }
        val rankedOptionViews = options
            .sortedWith(compareBy<VoteOptionView> { rankedByOptionId.getValue(it.id).dislike }
                .thenByDescending { rankedByOptionId.getValue(it.id).rawScore }
                .thenByDescending { rankedByOptionId.getValue(it.id).good }
                .thenBy { rankedByOptionId.getValue(it.id).maxBudget }
                .thenBy { rankedByOptionId.getValue(it.id).travelMinutes })
            .mapIndexed { index, option ->
                val score = rankedByOptionId.getValue(option.id)
                VoteAggregateOptionView(
                    optionId = option.id,
                    label = option.label,
                    counts = countsByOption.getValue(option.id),
                    rawScore = score.rawScore,
                    rank = index + 1,
                )
            }

        return VoteAggregateView(
            totalParticipants = totalParticipants,
            respondedParticipants = respondedParticipants,
            unresolvedParticipants = unresolvedParticipants,
            canEarlyClose = if (rankedOptionViews.isNotEmpty()) voteScoringService.canEarlyCloseLocationVote(scoredCounts, unresolvedParticipants) else null,
            leadingOptionIds = rankedOptionViews.takeWhile { it.rank == 1 }.map { it.optionId }.ifEmpty {
                rankedOptionViews.firstOrNull()?.let { listOf(it.optionId) } ?: emptyList()
            },
            options = rankedOptionViews,
        )
    }

    private fun buildBinaryAggregates(
        options: List<VoteOptionView>,
        responses: List<VoteResponseRecord>,
        totalParticipants: Int,
        respondedParticipants: Int,
        unresolvedParticipants: Int,
    ): VoteAggregateView {
        val countsByOption = buildCountsByOption(options, responses)
        val rankedOptionViews = options
            .sortedWith(compareByDescending<VoteOptionView> { countsByOption.getValue(it.id)["yes"] ?: 0 }
                .thenBy { countsByOption.getValue(it.id)["no"] ?: 0 }
                .thenBy { it.label })
            .mapIndexed { index, option ->
                val counts = countsByOption.getValue(option.id)
                VoteAggregateOptionView(
                    optionId = option.id,
                    label = option.label,
                    counts = counts,
                    rawScore = (counts["yes"] ?: 0).toDouble() - (counts["no"] ?: 0).toDouble(),
                    rank = index + 1,
                )
            }

        return VoteAggregateView(
            totalParticipants = totalParticipants,
            respondedParticipants = respondedParticipants,
            unresolvedParticipants = unresolvedParticipants,
            canEarlyClose = null,
            leadingOptionIds = rankedOptionViews.takeWhile { it.rank == 1 }.map { it.optionId }.ifEmpty {
                rankedOptionViews.firstOrNull()?.let { listOf(it.optionId) } ?: emptyList()
            },
            options = rankedOptionViews,
        )
    }

    private fun buildCountsByOption(
        options: List<VoteOptionView>,
        responses: List<VoteResponseRecord>,
    ): Map<UUID, Map<String, Int>> {
        val grouped = responses.groupBy { requireNotNull(it.optionId) }
        return options.associate { option ->
            val counts = grouped[option.id].orEmpty()
                .groupingBy { requireNotNull(it.responseValue).literal }
                .eachCount()
            option.id to counts
        }
    }

    private fun updateLinkedDecisionSnapshot(vote: VoteRecord, response: VoteResponseView, closedByHost: Boolean) {
        val decisionId = vote.decisionId ?: return
        val decisionRecord = dsl.selectFrom(DECISION)
            .where(DECISION.ID.eq(decisionId))
            .fetchOne()
            ?: return

        val payload = linkedMapOf(
            "phase" to "vote",
            "decisionId" to decisionId,
            "vote" to linkedMapOf(
                "voteId" to response.voteId,
                "title" to decisionRecord.title,
                "voteType" to response.voteType,
                "status" to response.status.name.lowercase(),
                "opensAt" to response.opensAt?.toString(),
                "deadlineAt" to response.deadlineAt?.toString(),
                "closedAt" to response.closedAt?.toString(),
                "closedByHost" to closedByHost,
                "leadingOptionIds" to response.aggregates?.leadingOptionIds.orEmpty(),
                "leadingOptions" to response.aggregates?.options.orEmpty().filter { it.rank == 1 },
                "aggregate" to response.aggregates,
            ),
        )

        val update = dsl.update(DECISION)
            .set(DECISION.PROPOSED_RESULT, jsonOf(payload))

        if (decisionRecord.status == DecisionStatus.proposed || decisionRecord.status == DecisionStatus.in_discussion || decisionRecord.status == DecisionStatus.voting) {
            update.set(DECISION.STATUS, DecisionStatus.voting)
        }

        update.where(DECISION.ID.eq(decisionId)).execute()
    }

    private fun requireMeeting(meetingId: UUID) {
        val exists = dsl.fetchExists(
            dsl.selectOne()
                .from(MEETING)
                .where(MEETING.ID.eq(meetingId)),
        )
        if (!exists) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "meeting not found")
        }
    }

    private fun requireThread(meetingId: UUID, threadId: UUID) {
        val exists = dsl.fetchExists(
            dsl.selectOne()
                .from(TOPIC_THREAD)
                .where(TOPIC_THREAD.ID.eq(threadId))
                .and(TOPIC_THREAD.MEETING_ID.eq(meetingId)),
        )
        if (!exists) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "thread does not belong to the meeting")
        }
    }

    private fun getRequiredVote(voteId: UUID): VoteRecord =
        dsl.selectFrom(VOTE)
            .where(VOTE.ID.eq(voteId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "vote not found")

    private fun getVoteOptions(voteId: UUID): List<VoteOptionRecord> =
        dsl.selectFrom(VOTE_OPTION)
            .where(VOTE_OPTION.VOTE_ID.eq(voteId))
            .orderBy(VOTE_OPTION.SORT_ORDER.asc(), VOTE_OPTION.LABEL.asc())
            .fetch()

    private fun defaultScoringRule(voteType: VoteType): Map<String, Any> =
        when (voteType) {
            VoteType.date_matrix -> mapOf(
                "version" to "mvp-v1",
                "model" to "date_matrix",
                "weights" to mapOf("available" to 1.0, "maybe" to 0.5, "unavailable" to -2.0),
            )
            VoteType.package_preference -> mapOf(
                "version" to "mvp-v1",
                "model" to "package_preference",
                "weights" to mapOf("good" to 1.0, "okay" to 0.2, "dislike" to -1.5),
            )
            VoteType.binary_confirm -> mapOf(
                "version" to "mvp-v1",
                "model" to "binary_confirm",
                "weights" to mapOf("yes" to 1.0, "no" to -1.0),
            )
        }

    private fun defaultDecisionType(voteType: VoteType): DecisionType =
        when (voteType) {
            VoteType.date_matrix -> DecisionType.date
            VoteType.package_preference -> DecisionType.location
            VoteType.binary_confirm -> DecisionType.other
        }

    private fun allowedResponseValues(voteType: VoteType): Set<String> =
        when (voteType) {
            VoteType.date_matrix -> setOf("available", "maybe", "unavailable")
            VoteType.package_preference -> setOf("good", "okay", "dislike")
            VoteType.binary_confirm -> setOf("yes", "no")
        }

    private fun VoteStatus.toDto(): VoteStatusDto =
        when (this) {
            VoteStatus.draft -> VoteStatusDto.DRAFT
            VoteStatus.open -> VoteStatusDto.OPEN
            VoteStatus.closed -> VoteStatusDto.CLOSED
            VoteStatus.superseded -> VoteStatusDto.SUPERSEDED
        }

    private fun String.toVoteType(): VoteType =
        try {
            VoteType.valueOf(this)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported vote type: $this")
        }

    private fun String.toDecisionType(): DecisionType =
        try {
            DecisionType.valueOf(this)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported decision type: $this")
        }

    private fun String.toVoteResponseValue(): VoteResponseValue =
        try {
            VoteResponseValue.valueOf(this)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported response value: $this")
        }

    private fun VoteOptionView.extractDate(): LocalDate {
        val payloadDate = payload["date"]?.toString()
        return try {
            LocalDate.parse(payloadDate ?: label)
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "date vote option must provide ISO-8601 date metadata")
        }
    }

    private fun Any?.toIntValue(): Int =
        when (this) {
            is Int -> this
            is Long -> this.toInt()
            is Double -> this.toInt()
            is Float -> this.toInt()
            is BigDecimal -> this.toInt()
            is String -> this.toIntOrNull() ?: 0
            else -> 0
        }

    private fun jsonOf(value: Any): JSON = JSON.valueOf(objectMapper.writeValueAsString(value))

    private fun jsonToMap(value: JSON?): Map<String, Any?> {
        if (value == null || value.data().isBlank()) {
            return emptyMap()
        }

        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(value.data(), Map::class.java) as Map<String, Any?>
    }
}
