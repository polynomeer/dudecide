package com.dudecide.app.domain.chatimport

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason

/**
 * Claude via the official Java SDK with structured outputs. Effort is set per stage because the defaults differ by
 * model. Stages listed in [fallbackStages] opt into the server-side refusal fallback, which routes a refused
 * request to another model instead of failing it.
 */
class AnthropicJsonTransport(
    private val client: AnthropicClient,
    private val efforts: Map<String, OutputConfig.Effort>,
    private val fallbackStages: Set<String>,
) : JsonCompletionTransport {

    override val provider: String = "anthropic"

    override fun complete(request: JsonCompletionRequest): JsonCompletion {
        val schema = JsonOutputFormat.Schema.builder().apply {
            request.schema.forEach { (key, value) -> putAdditionalProperty(key, JsonValue.from(value)) }
        }.build()
        val output = OutputConfig.builder()
            .format(JsonOutputFormat.builder().schema(schema).build())
            .apply { efforts[request.stage]?.let { effort(it) } }
            .build()

        val params = MessageCreateParams.builder()
            .model(request.model)
            .maxTokens(request.maxTokens)
            .system(request.system)
            .addUserMessage(request.user)
            .outputConfig(output)
            .apply {
                if (request.stage in fallbackStages) {
                    putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                }
            }
            .build()

        val message = client.messages().create(params)
        when (message.stopReason().orElse(null)) {
            StopReason.REFUSAL -> throw LlmResponseException("${request.stage} request was refused")
            StopReason.MAX_TOKENS -> throw LlmResponseException("${request.stage} response hit max_tokens")
            else -> Unit
        }
        val text = message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
        return JsonCompletion(
            json = text,
            inputTokens = message.usage().inputTokens(),
            outputTokens = message.usage().outputTokens(),
        )
    }

    private companion object {
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
    }
}
