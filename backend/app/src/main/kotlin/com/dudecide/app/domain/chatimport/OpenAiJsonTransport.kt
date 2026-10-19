package com.dudecide.app.domain.chatimport

import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.models.ReasoningEffort
import com.openai.models.ResponseFormatJsonSchema
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams

/** OpenAI via the official Java SDK, Chat Completions with a strict JSON schema response format. */
class OpenAiJsonTransport(
    private val client: OpenAIClient,
    private val efforts: Map<String, ReasoningEffort>,
) : JsonCompletionTransport {

    override val provider: String = "openai"

    override fun complete(request: JsonCompletionRequest): JsonCompletion {
        val schema = ResponseFormatJsonSchema.JsonSchema.Schema.builder().apply {
            request.schema.forEach { (key, value) -> putAdditionalProperty(key, JsonValue.from(value)) }
        }.build()
        val params = ChatCompletionCreateParams.builder()
            .model(request.model)
            .addSystemMessage(request.system)
            .addUserMessage(request.user)
            .maxCompletionTokens(request.maxTokens)
            .responseFormat(
                ResponseFormatJsonSchema.builder()
                    .jsonSchema(
                        ResponseFormatJsonSchema.JsonSchema.builder()
                            .name(request.schemaName)
                            .strict(true)
                            .schema(schema)
                            .build(),
                    )
                    .build(),
            )
            .apply { efforts[request.stage]?.let { reasoningEffort(it) } }
            .build()

        val completion = client.chat().completions().create(params)
        val choice = completion.choices().firstOrNull() ?: throw LlmResponseException("${request.stage} response had no choices")
        choice.message().refusal().orElse(null)?.let { throw LlmResponseException("${request.stage} request was refused") }
        if (choice.finishReason() == ChatCompletion.Choice.FinishReason.LENGTH) {
            throw LlmResponseException("${request.stage} response hit the token limit")
        }
        val usage = completion.usage().orElse(null)
        return JsonCompletion(
            json = choice.message().content().orElse(""),
            inputTokens = usage?.promptTokens() ?: 0,
            outputTokens = usage?.completionTokens() ?: 0,
        )
    }
}
