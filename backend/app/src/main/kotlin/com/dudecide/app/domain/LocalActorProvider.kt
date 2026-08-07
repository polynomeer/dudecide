package com.dudecide.app.domain

import com.dudecide.app.jooq.tables.AppUser.Companion.APP_USER
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class LocalActorProvider(
    private val dsl: DSLContext,
) {
    private val localHostId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Transactional
    fun ensureHostUser(): UUID {
        val exists = dsl.fetchExists(
            DSL.selectOne()
                .from(APP_USER)
                .where(APP_USER.ID.eq(localHostId)),
        )

        if (!exists) {
            dsl.insertInto(APP_USER)
                .set(APP_USER.ID, localHostId)
                .set(APP_USER.DISPLAY_NAME, "Local Host")
                .set(APP_USER.STATUS, "active")
                .execute()
        }

        return localHostId
    }
}
