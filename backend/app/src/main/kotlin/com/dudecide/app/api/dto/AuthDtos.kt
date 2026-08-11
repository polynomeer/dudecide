package com.dudecide.app.api.dto

import java.time.OffsetDateTime
import java.util.UUID

data class CreateGuestSessionRequest(
    val displayName: String,
)

data class CurrentActorResponse(
    val userId: UUID,
    val displayName: String,
    val authSource: String,
    val createdAt: OffsetDateTime? = null,
)
