package com.dudecide.app.domain

import com.dudecide.app.jooq.tables.AppUser.Companion.APP_USER
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Resolves and provisions the calling actor. Provisioning commits in its own transaction: callers are often
 * read-only transactions, and the actor row should exist even if the request that first saw the actor fails.
 */
@Component
class LocalActorProvider(
    private val dsl: DSLContext,
    // Development only: trusts X-Actor-Id and falls back to the shared Local Host user. Anyone could act as anyone,
    // so it stays off unless a developer turns it on for curl and seed-data work.
    @Value("\${dudecide.auth.dev-actors-enabled:false}") private val devActorsEnabled: Boolean = false,
) {
    companion object {
        const val SESSION_ACTOR_ID_KEY = "actorUserId"
        const val SESSION_ACTOR_NAME_KEY = "actorDisplayName"
    }

    private val localHostId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    init {
        if (devActorsEnabled) {
            LoggerFactory.getLogger(javaClass).warn("dudecide.auth.dev-actors-enabled is on: callers are not authenticated")
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun ensureHostUser(): UUID {
        ensureUser(localHostId, "Local Host")
        return localHostId
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun currentUserId(): UUID {
        return currentActor().userId
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun currentActor(): CurrentActor {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val sessionActorId = request?.getSession(false)?.getAttribute(SESSION_ACTOR_ID_KEY)?.toString()?.trim().orEmpty()
        val sessionActorName = request?.getSession(false)?.getAttribute(SESSION_ACTOR_NAME_KEY)?.toString()?.trim().orEmpty().ifBlank { null }
        if (sessionActorId.isNotBlank()) {
            return buildActor(sessionActorId, sessionActorName, "session")
        }
        if (!devActorsEnabled) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required")
        }

        val actorIdHeader = request?.getHeader("X-Actor-Id")?.trim().orEmpty()
        val actorName = request?.getHeader("X-Actor-Name")?.trim().orEmpty().ifBlank { null }
        if (actorIdHeader.isNotBlank()) {
            return buildActor(actorIdHeader, actorName, "header")
        }

        val hostId = ensureHostUser()
        val hostRecord = requireUser(hostId)
        return CurrentActor(
            userId = hostId,
            displayName = requireNotNull(hostRecord.displayName),
            authSource = "local_host",
            createdAt = hostRecord.createdAt,
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createSessionActor(displayName: String): CurrentActor {
        val actorId = UUID.randomUUID()
        ensureUser(actorId, displayName)
        val record = requireUser(actorId)
        return CurrentActor(
            userId = actorId,
            displayName = requireNotNull(record.displayName),
            authSource = "session",
            createdAt = record.createdAt,
        )
    }

    private fun ensureUser(userId: UUID, displayName: String) {
        val currentName = dsl.select(APP_USER.DISPLAY_NAME)
            .from(APP_USER)
            .where(APP_USER.ID.eq(userId))
            .fetchOptional(APP_USER.DISPLAY_NAME)

        if (currentName.isEmpty) {
            dsl.insertInto(APP_USER)
                .set(APP_USER.ID, userId)
                .set(APP_USER.DISPLAY_NAME, displayName)
                .set(APP_USER.STATUS, "active")
                .execute()
            return
        }

        if (currentName.get() != displayName) {
            dsl.update(APP_USER)
                .set(APP_USER.DISPLAY_NAME, displayName)
                .where(APP_USER.ID.eq(userId))
                .execute()
        }
    }

    private fun buildActor(rawActorId: String, displayName: String?, authSource: String): CurrentActor {
        val actorId = runCatching { UUID.fromString(rawActorId) }
            .getOrElse {
                val sourceLabel = if (authSource == "session") "session actor id" else "X-Actor-Id header"
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid $sourceLabel")
            }
        ensureUser(actorId, displayName ?: "User ${actorId.toString().take(8)}")
        val record = requireUser(actorId)
        return CurrentActor(
            userId = actorId,
            displayName = requireNotNull(record.displayName),
            authSource = authSource,
            createdAt = record.createdAt,
        )
    }

    private fun requireUser(userId: UUID) =
        dsl.selectFrom(APP_USER)
            .where(APP_USER.ID.eq(userId))
            .fetchOne()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "user not found")

    data class CurrentActor(
        val userId: UUID,
        val displayName: String,
        val authSource: String,
        val createdAt: OffsetDateTime? = null,
    )
}
