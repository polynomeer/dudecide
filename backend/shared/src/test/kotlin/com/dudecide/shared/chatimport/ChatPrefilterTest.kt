package com.dudecide.shared.chatimport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ChatPrefilterTest {

    private val prefilter = ChatPrefilter()

    @Test
    fun `laughter emoji and punctuation only messages are noise`() {
        listOf("ㅋㅋㅋㅋ", "ㅎㅎ", "ㅠㅠ", "하하하", "ㅋㅋㅋ😂", "😂😂", "...", "?!", "lol", "  ").forEach {
            assertTrue(prefilter.isNoise(it), "expected noise: '$it'")
        }
    }

    @Test
    fun `single visible character is noise unless it is a stance token`() {
        assertTrue(prefilter.isNoise("ㅋ"))
        assertTrue(prefilter.isNoise("헐"))
        assertFalse(prefilter.isNoise("응"))
        assertFalse(prefilter.isNoise("콜!"))
    }

    @Test
    fun `stance reactions are kept as support or opposition signals`() {
        listOf("ㅇㅇ", "ㅇㅋ", "ㄱㄱ", "좋아요~", "OK", "ㄴㄴ", "반대").forEach {
            assertFalse(prefilter.isNoise(it), "expected kept: '$it'")
        }
    }

    @Test
    fun `messages with content are kept even when they contain laughter`() {
        assertFalse(prefilter.isNoise("ㅋㅋ 18일 좋다"))
        assertFalse(prefilter.isNoise("애월 숙소 어때"))
        assertFalse(prefilter.isNoise("18"))
    }

    @Test
    fun `filter partitions messages and preserves order`() {
        val messages = listOf("18일 어때", "ㅋㅋㅋ", "ㅇㅇ", "😂").mapIndexed { index, body ->
            ImportedMessage("m$index", "A", Instant.parse("2026-10-02T12:00:00Z").plusSeconds(index * 60L), body)
        }

        val result = prefilter.filter(messages)

        assertEquals(listOf("m0", "m2"), result.kept.map { it.id })
        assertEquals(listOf("m1", "m3"), result.dropped.map { it.id })
    }
}
