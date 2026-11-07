package com.dudecide.app.domain

import com.dudecide.app.api.dto.CreateGuestSessionRequest
import com.dudecide.app.api.dto.CurrentActorResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class AuthSessionService(
    private val localActorProvider: LocalActorProvider,
) {

    @Transactional
    fun createGuestSession(request: CreateGuestSessionRequest, servletRequest: HttpServletRequest): CurrentActorResponse {
        val displayName = request.displayName.trim()
        if (displayName.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName must not be blank")
        }

        // A browser that already has an identity keeps it; a new user would lose access to its meetings.
        localActorProvider.sessionActorOrNull()?.let { return it.toResponse() }

        val actor = localActorProvider.createSessionActor(displayName)
        rememberActor(servletRequest, actor.userId, actor.displayName)
        return actor.toResponse()
    }

    @Transactional(readOnly = true)
    fun getCurrentActor(): CurrentActorResponse = localActorProvider.currentActor().toResponse()

    fun rememberActor(servletRequest: HttpServletRequest, userId: UUID, displayName: String) {
        val session = servletRequest.getSession(true)
        session.setAttribute(LocalActorProvider.SESSION_ACTOR_ID_KEY, userId.toString())
        session.setAttribute(LocalActorProvider.SESSION_ACTOR_NAME_KEY, displayName)
    }

    fun clearSession(servletRequest: HttpServletRequest) {
        servletRequest.getSession(false)?.invalidate()
    }

    private fun LocalActorProvider.CurrentActor.toResponse() =
        CurrentActorResponse(
            userId = userId,
            displayName = displayName,
            authSource = authSource,
            createdAt = createdAt,
        )
}
