package com.dudecide.app.domain

import com.dudecide.app.api.dto.CreateGuestSessionRequest
import org.h2.jdbcx.JdbcDataSource
import org.h2.tools.RunScript
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.io.StringReader
import java.util.UUID

class AuthSessionServiceTest {

    @Test
    fun `creates guest session and resolves current actor from session`() {
        val dsl = dsl()
        val localActorProvider = LocalActorProvider(dsl)
        val authSessionService = AuthSessionService(localActorProvider)
        val request = MockHttpServletRequest()

        val created = authSessionService.createGuestSession(
            CreateGuestSessionRequest(displayName = "Guest Session"),
            request,
        )

        assertEquals("session", created.authSource)
        assertNotNull(request.getSession(false))

        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        try {
            val current = authSessionService.getCurrentActor()
            assertEquals(created.userId, current.userId)
            assertEquals("Guest Session", current.displayName)
            assertEquals("session", current.authSource)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    fun `session actor takes precedence over actor header`() {
        val dsl = dsl()
        val localActorProvider = LocalActorProvider(dsl)
        val authSessionService = AuthSessionService(localActorProvider)
        val request = MockHttpServletRequest()
        val sessionActor = authSessionService.createGuestSession(
            CreateGuestSessionRequest(displayName = "Session User"),
            request,
        )
        request.addHeader("X-Actor-Id", UUID.randomUUID().toString())
        request.addHeader("X-Actor-Name", "Header User")

        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        try {
            val current = localActorProvider.currentActor()
            assertEquals(sessionActor.userId, current.userId)
            assertEquals("Session User", current.displayName)
            assertEquals("session", current.authSource)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    private fun dsl() = JdbcDataSource().apply {
        setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
        user = "sa"
        password = ""
    }.let { dataSource ->
        dataSource.connection.use { connection ->
            RunScript.execute(
                connection,
                StringReader(
                    """
                    CREATE TABLE app_user (
                        id UUID DEFAULT random_uuid() PRIMARY KEY,
                        display_name VARCHAR(120) NOT NULL,
                        avatar_url TEXT,
                        status VARCHAR(32) NOT NULL DEFAULT 'active',
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                    );
                    """.trimIndent(),
                ),
            )
        }
        DSL.using(dataSource, SQLDialect.H2)
    }
}
