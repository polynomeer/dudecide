package com.dudecide.app.api.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

class RequestDtoDeserializationTest {

    // Same builder Spring Boot uses for its ObjectMapper; it auto-registers the Kotlin module when present.
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()

    @Test
    fun `applies kotlin defaults when message attachments are omitted`() {
        val create = objectMapper.readValue<CreateMessageRequest>("""{"body":"hello"}""")
        val update = objectMapper.readValue<UpdateMessageRequest>("""{"body":"edited"}""")

        assertEquals("hello", create.body)
        assertTrue(create.attachments.isEmpty())
        assertEquals("edited", update.body)
        assertTrue(update.attachments.isEmpty())
    }

    @Test
    fun `applies kotlin defaults when vote option payload is omitted`() {
        val option = objectMapper.readValue<CreateVoteOptionRequest>(
            """{"optionType":"date","label":"Aug 1"}""",
        )

        assertEquals(emptyMap<String, Any?>(), option.payload)
        assertEquals(null, option.sortOrder)
    }
}
