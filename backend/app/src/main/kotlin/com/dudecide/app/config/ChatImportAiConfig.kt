package com.dudecide.app.config

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.OutputConfig
import com.dudecide.app.domain.chatimport.AnthropicJsonTransport
import com.dudecide.app.domain.chatimport.FakeLlmClient
import com.dudecide.app.domain.chatimport.LlmClient
import com.dudecide.app.domain.chatimport.OpenAiJsonTransport
import com.dudecide.app.domain.chatimport.PromptedLlmClient
import com.fasterxml.jackson.databind.ObjectMapper
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ReasoningEffort
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * `provider`: fake (default), anthropic, or openai. Credentials come from the SDKs' environment variables
 * (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`). Blank models fall back to provider defaults where one exists.
 */
@ConfigurationProperties("dudecide.ai")
data class ChatImportAiProperties(
    val provider: String = "fake",
    val mapModel: String = "",
    val reduceModel: String = "",
    val mapEffort: String = "low",
    val reduceEffort: String = "medium",
    val maxTokens: Long = 16_000,
    val refusalFallback: Boolean = true,
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
    fun llmClient(properties: ChatImportAiProperties, objectMapper: ObjectMapper, meterRegistry: MeterRegistry): LlmClient =
        when (properties.provider) {
            "fake" -> FakeLlmClient()
            "anthropic" -> PromptedLlmClient(
                transport = AnthropicJsonTransport(
                    client = AnthropicOkHttpClient.fromEnv(),
                    efforts = mapOf(
                        "map" to OutputConfig.Effort.of(properties.mapEffort),
                        "reduce" to OutputConfig.Effort.of(properties.reduceEffort),
                    ),
                    // Haiku has no server-side refusal fallback; reduce runs on Opus by default.
                    fallbackStages = if (properties.refusalFallback) setOf("reduce") else emptySet(),
                ),
                mapModel = properties.mapModel.ifBlank { ANTHROPIC_MAP_MODEL },
                reduceModel = properties.reduceModel.ifBlank { ANTHROPIC_REDUCE_MODEL },
                maxTokens = properties.maxTokens,
                objectMapper = objectMapper,
                meterRegistry = meterRegistry,
            )
            "openai" -> PromptedLlmClient(
                transport = OpenAiJsonTransport(
                    client = OpenAIOkHttpClient.fromEnv(),
                    efforts = mapOf(
                        "map" to ReasoningEffort.of(properties.mapEffort),
                        "reduce" to ReasoningEffort.of(properties.reduceEffort),
                    ),
                ),
                mapModel = properties.mapModel.ifBlank { error("dudecide.ai.map-model is required for provider openai") },
                reduceModel = properties.reduceModel.ifBlank { error("dudecide.ai.reduce-model is required for provider openai") },
                maxTokens = properties.maxTokens,
                objectMapper = objectMapper,
                meterRegistry = meterRegistry,
            )
            else -> error("unknown dudecide.ai.provider=${properties.provider}; use fake, anthropic, or openai")
        }

    private companion object {
        const val ANTHROPIC_MAP_MODEL = "claude-haiku-5-5"
        const val ANTHROPIC_REDUCE_MODEL = "claude-opus-5-5"
    }
}
