package com.dudecide.app.domain

import com.dudecide.app.api.dto.VoteAggregateOptionView
import com.dudecide.app.api.dto.VoteAggregateView
import com.dudecide.app.api.dto.VoteOptionView
import com.dudecide.app.api.dto.VoteResponseView
import com.dudecide.app.api.dto.VoteStatusDto
import com.dudecide.app.api.dto.VoteSubmissionItem
import com.dudecide.app.api.dto.VoteSubmissionRequest
import com.dudecide.app.jooq.enums.DecisionStatus
import com.dudecide.app.jooq.enums.VoteResponseValue
import com.dudecide.app.jooq.enums.VoteStatus
import com.dudecide.app.jooq.enums.VoteType
import com.dudecide.app.jooq.tables.Decision.Companion.DECISION
import com.dudecide.app.jooq.tables.Membership.Companion.MEMBERSHIP
import com.dudecide.app.jooq.tables.Vote.Companion.VOTE
import com.dudecide.app.jooq.tables.VoteOption.Companion.VOTE_OPTION
import com.dudecide.app.jooq.tables.VoteResponse.Companion.VOTE_RESPONSE
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
import java.util.UUID

@Service
class VoteService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    private val localActorProvider: LocalActorProvider,
) {
    private val voteScoringService = VoteScoringService()

    @Transactional(readOnly = true)
    fun getVote(voteId: UUID): VoteResponseView {
        val actorId = localActorProvider.ensureHostUser()
        val vote = getRequiredVote(voteId)
        val options = getVoteOptions(voteId)
        return buildVoteResponse(vote, options, actorId)
    }

    @Transactional
    fun submitResponses(voteId: UUID, request: VoteSubmissionRequest): VoteResponseView {
        val actorId = localActorProvider.ensureHostUser()
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
        updateLinkedDecisionSnapshot(vote, response)
        return response
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

        val scored = voteScoringService.scoreDates(
            options.map { option ->
                val counts = countsByOption.getValue(option.id)
                DateVoteCounts(
                    date = option.extractDate(),
                    available = counts["available"] ?: 0,
                    maybe = counts["maybe"] ?: 0,
                    unavailable = counts["unavailable"] ?: 0,
                )
            },
        )

        val rankedByDate = scored.associateBy { it.date }
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
            canEarlyClose = if (rankedOptionViews.isNotEmpty()) {
                voteScoringService.canEarlyCloseDateVote(scored.map {
                    DateVoteCounts(
                        date = it.date,
                        available = it.available,
                        maybe = it.maybe,
                        unavailable = it.unavailable,
                    )
                }, unresolvedParticipants)
            } else {
                null
            },
            leadingOptionIds = rankedOptionViews.firstOrNull()?.let { listOf(it.optionId) } ?: emptyList(),
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

        val scored = voteScoringService.scoreLocations(
            options.map { option ->
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
            },
        )

        val rankedByOptionId = scored.associateBy { UUID.fromString(it.optionId) }
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
            canEarlyClose = if (rankedOptionViews.isNotEmpty()) {
                voteScoringService.canEarlyCloseLocationVote(scored.map {
                    LocationVoteCounts(
                        optionId = it.optionId,
                        good = it.good,
                        okay = it.okay,
                        dislike = it.dislike,
                        maxBudget = it.maxBudget,
                        travelMinutes = it.travelMinutes,
                    )
                }, unresolvedParticipants)
            } else {
                null
            },
            leadingOptionIds = rankedOptionViews.firstOrNull()?.let { listOf(it.optionId) } ?: emptyList(),
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
                    rawScore = (counts["yes"] ?: 0) - (counts["no"] ?: 0).toDouble(),
                    rank = index + 1,
                )
            }

        return VoteAggregateView(
            totalParticipants = totalParticipants,
            respondedParticipants = respondedParticipants,
            unresolvedParticipants = unresolvedParticipants,
            canEarlyClose = null,
            leadingOptionIds = rankedOptionViews.firstOrNull()?.let { listOf(it.optionId) } ?: emptyList(),
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

    private fun updateLinkedDecisionSnapshot(vote: VoteRecord, response: VoteResponseView) {
        val decisionId = vote.decisionId ?: return

        val payload = linkedMapOf(
            "voteId" to response.voteId,
            "voteType" to response.voteType,
            "status" to response.status.name.lowercase(),
            "leadingOptionIds" to response.aggregates?.leadingOptionIds.orEmpty(),
            "aggregates" to response.aggregates,
        )

        val decisionRecord = dsl.selectFrom(DECISION)
            .where(DECISION.ID.eq(decisionId))
            .fetchOne()
            ?: return

        val update = dsl.update(DECISION)
            .set(DECISION.PROPOSED_RESULT, JSON.valueOf(objectMapper.writeValueAsString(payload)))

        if (decisionRecord.status == DecisionStatus.proposed || decisionRecord.status == DecisionStatus.in_discussion || decisionRecord.status == DecisionStatus.voting) {
            update.set(DECISION.STATUS, DecisionStatus.voting)
        }

        update.where(DECISION.ID.eq(decisionId)).execute()
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

    private fun jsonToMap(value: JSON?): Map<String, Any?> {
        if (value == null || value.data().isBlank()) {
            return emptyMap()
        }

        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(value.data(), Map::class.java) as Map<String, Any?>
    }
}
