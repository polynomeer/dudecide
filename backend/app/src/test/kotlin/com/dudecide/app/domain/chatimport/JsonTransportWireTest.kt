package com.dudecide.app.domain.chatimport

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.OutputConfig
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ReasoningEffort
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

/** Checks what the SDK transports put on the wire, against a local server instead of a paid API. */
class JsonTransportWireTest {

    private val objectMapper = ObjectMapper()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val captured = mutableListOf<Captured>()
    private var responseBody = "{}"

    private data class Captured(val path: String, val headers: Map<String, List<String>>, val body: JsonNode)

    init {
        server.createContext("/") { exchange ->
            captured += Captured(
                path = exchange.requestURI.path,
                headers = exchange.requestHeaders.mapKeys { it.key.lowercase() },
                body = objectMapper.readTree(exchange.requestBody.readAllBytes()),
            )
            val bytes = responseBody.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stop() = server.stop(0)

    private val baseUrl get() = "http://127.0.0.1:${server.address.port}"

    @Test
    fun `anthropic transport sends structured output, effort, and the reduce fallback`() {
        val transport = AnthropicJsonTransport(
            client = AnthropicOkHttpClient.builder().apiKey("test-key").baseUrl(baseUrl).maxRetries(0).build(),
            efforts = mapOf("map" to OutputConfig.Effort.LOW, "reduce" to OutputConfig.Effort.MEDIUM),
            fallbackStages = setOf("reduce"),
        )
        responseBody = anthropicResponse(stopReason = "end_turn", text = """{"subjects":[]}""")

        val map = transport.complete(request("map", "claude-haiku-5-5"))
        transport.complete(request("reduce", "claude-opus-5-5"))

        assertEquals("""{"subjects":[]}""", map.json)
        assertEquals(10, map.inputTokens)
        val (mapCall, reduceCall) = captured
        assertEquals("/v1/messages", mapCall.path)
        assertEquals("claude-haiku-5-5", mapCall.body["model"].asText())
        assertEquals("system prompt", mapCall.body["system"].asText())
        assertEquals("low", mapCall.body["output_config"]["effort"].asText())
        assertEquals("json_schema", mapCall.body["output_config"]["format"]["type"].asText())
        assertEquals("object", mapCall.body["output_config"]["format"]["schema"]["type"].asText())
        assertFalse(mapCall.body.has("fallbacks"))
        assertEquals("default", reduceCall.body["fallbacks"].asText())
        assertTrue(reduceCall.headers.getValue("anthropic-beta").any { "server-side-fallback-2026-07-01" in it })
        assertEquals("medium", reduceCall.body["output_config"]["effort"].asText())
    }

    @Test
    fun `anthropic transport rejects refusals and truncated output`() {
        val transport = AnthropicJsonTransport(
            client = AnthropicOkHttpClient.builder().apiKey("test-key").baseUrl(baseUrl).maxRetries(0).build(),
            efforts = emptyMap(),
            fallbackStages = emptySet(),
        )

        responseBody = anthropicResponse(stopReason = "refusal", text = "")
        assertThrows(LlmResponseException::class.java) { transport.complete(request("map", "claude-haiku-5-5")) }
        responseBody = anthropicResponse(stopReason = "max_tokens", text = "{\"subj")
        assertThrows(LlmResponseException::class.java) { transport.complete(request("map", "claude-haiku-5-5")) }
    }

    @Test
    fun `openai transport sends a strict json schema response format`() {
        val transport = OpenAiJsonTransport(
            client = OpenAIOkHttpClient.builder().apiKey("test-key").baseUrl("$baseUrl/v1").maxRetries(0).build(),
            efforts = mapOf("map" to ReasoningEffort.LOW),
        )
        responseBody = """
            {"id":"c1","object":"chat.completion","created":1,"model":"test-model",
             "choices":[{"index":0,"finish_reason":"stop","logprobs":null,
                         "message":{"role":"assistant","content":"{\"subjects\":[]}","refusal":null}}],
             "usage":{"prompt_tokens":7,"completion_tokens":3,"total_tokens":10}}
        """.trimIndent()

        val result = transport.complete(request("map", "test-model"))

        assertEquals("""{"subjects":[]}""", result.json)
        assertEquals(7, result.inputTokens)
        val call = captured.single()
        assertEquals("/v1/chat/completions", call.path)
        assertEquals("json_schema", call.body["response_format"]["type"].asText())
        assertTrue(call.body["response_format"]["json_schema"]["strict"].asBoolean())
        assertEquals("chat_episode_labels", call.body["response_format"]["json_schema"]["name"].asText())
        assertEquals("low", call.body["reasoning_effort"].asText())
        assertEquals("system", call.body["messages"][0]["role"].asText())
    }

    private fun request(stage: String, model: String) = JsonCompletionRequest(
        stage = stage,
        model = model,
        system = "system prompt",
        user = """{"messages":[]}""",
        schemaName = "chat_episode_labels",
        schema = ChatImportPrompts.MAP_SCHEMA,
        maxTokens = 16_000,
    )

    private fun anthropicResponse(stopReason: String, text: String): String =
        objectMapper.writeValueAsString(
            mapOf(
                "id" to "msg_test",
                "type" to "message",
                "role" to "assistant",
                "model" to "claude-test",
                "content" to listOf(mapOf("type" to "text", "text" to text)),
                "stop_reason" to stopReason,
                "stop_sequence" to null,
                "usage" to mapOf("input_tokens" to 10, "output_tokens" to 5),
            ),
        )
}
