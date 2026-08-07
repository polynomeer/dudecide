package com.dudecide.app.config

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.RenderNameCase
import org.jooq.conf.RenderNameStyle
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class JooqConfig {

    @Bean
    fun dslContext(dataSource: DataSource): DSLContext =
        DSL.using(
            dataSource,
            SQLDialect.POSTGRES,
            Settings()
                .withRenderNameCase(RenderNameCase.LOWER)
                .withRenderNameStyle(RenderNameStyle.AS_IS),
        )
}
