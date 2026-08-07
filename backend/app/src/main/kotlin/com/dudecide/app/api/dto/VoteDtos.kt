package com.dudecide.app.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class VoteSubmissionRequest(
    val responses: List<VoteSubmissionItem>,
)

data class VoteSubmissionItem(
    val optionId: UUID,
    @field:Schema(allowableValues = ["available", "maybe", "unavailable", "good", "okay", "dislike", "yes", "no"])
    val responseValue: String,
)

data class VoteResponseView(
    val voteId: UUID,
    val status: VoteStatusDto,
    val responses: List<VoteSubmissionItem>,
)

enum class VoteStatusDto {
    DRAFT,
    OPEN,
    CLOSED,
    SUPERSEDED,
}
