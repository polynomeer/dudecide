package com.dudecide.app.config

import com.dudecide.app.domain.chatimport.FakeLlmClient
import com.dudecide.app.domain.chatimport.LlmClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("dudecide.ai")
data class ChatImportAiProperties(
    val provider: String = "fake",
    val mapModel: String = "",
    val reduceModel: String = "",
    val timeoutSeconds: Long = 60,
    val maxConcurrency: Int = 4,
)

/** Free-tier import limits over a rolling 24 hours, counted from `chat_import` rows (deleted ones included). */
@ConfigurationProperties("dudecide.imports")
data class ChatImportLimitProperties(
    val dailyPerActor: Int = 3,
    val dailyPerMeeting: Int = 10,
)

@Configuration
@EnableConfigurationProperties(ChatImportAiProperties::class, ChatImportLimitProperties::class)
class ChatImportAiConfig {

    @Bean
    fun llmClient(properties: ChatImportAiProperties): LlmClient =
        when (properties.provider) {
            "fake" -> FakeLlmClient()
            else -> error("dudecide.ai.provider=${properties.provider} is not implemented yet; use 'fake'")
        }
}
