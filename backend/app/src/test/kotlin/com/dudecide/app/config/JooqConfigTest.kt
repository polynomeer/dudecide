package com.dudecide.app.config

import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class JooqConfigTest {

    @Test
    fun `jooq statements roll back with the surrounding spring transaction`() {
        val dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
            user = "sa"
        }
        val dsl = JooqConfig().dslContext(dataSource)
        val transactions = TransactionTemplate(DataSourceTransactionManager(dataSource))
        dsl.execute("create table probe (id int primary key)")

        assertThrows(IllegalStateException::class.java) {
            transactions.executeWithoutResult {
                dsl.execute("insert into probe values (1)")
                error("fail after write")
            }
        }
        transactions.executeWithoutResult { dsl.execute("insert into probe values (2)") }

        assertEquals(listOf(2), dsl.fetch("select id from probe").getValues(0, Int::class.java))
    }
}
