package com.dudecide.app.domain

import com.dudecide.app.jooq.tables.AppUser.Companion.APP_USER
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Component
class LocalActorProvider(
    private val dsl: DSLContext,
) {
    private val localHostId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Transactional
    fun ensureHostUser(): UUID {
        ensureUser(localHostId, "Local Host")
        return localHostId
    }

    @Transactional
    fun currentUserId(): UUID {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val actorIdHeader = request?.getHeader("X-Actor-Id")?.trim().orEmpty()
        val actorName = request?.getHeader("X-Actor-Name")?.trim().orEmpty().ifBlank { null }

        if (actorIdHeader.isBlank()) {
            return ensureHostUser()
        }

        val actorId = runCatching { UUID.fromString(actorIdHeader) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid X-Actor-Id header") }
        ensureUser(actorId, actorName ?: "User ${actorId.toString().take(8)}")
        return actorId
    }

    private fun ensureUser(userId: UUID, displayName: String) {
        val exists = dsl.fetchExists(
            DSL.selectOne()
                .from(APP_USER)
                .where(APP_USER.ID.eq(userId)),
        )

        if (!exists) {
            dsl.insertInto(APP_USER)
                .set(APP_USER.ID, userId)
                .set(APP_USER.DISPLAY_NAME, displayName)
                .set(APP_USER.STATUS, "active")
                .execute()
            return
        }

        dsl.update(APP_USER)
            .set(APP_USER.DISPLAY_NAME, displayName)
            .where(APP_USER.ID.eq(userId))
            .and(APP_USER.DISPLAY_NAME.ne(displayName))
            .execute()
    }
}
