package com.dudecide.shared.chatimport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ChatContentHasherTest {

    private val hasher = ChatContentHasher()
    private val at = Instant.parse("2026-10-02T12:14:00Z")

    @Test
    fun `hash is a lowercase sha256 hex string`() {
        val hash = hasher.hashAll(listOf(HashableMessage("Minji", at, "Let's go"))).single()

        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `seconds and whitespace differences do not change the hash`() {
        val first = hasher.hashAll(listOf(HashableMessage("Minji", at, "Let's  go\n"))).single()
        val second = hasher.hashAll(listOf(HashableMessage(" Minji", at.plusSeconds(42), "Let's go"))).single()

        assertEquals(first, second)
    }

    @Test
    fun `repeated identical messages in one upload get distinct hashes`() {
        val hashes = hasher.hashAll(List(2) { HashableMessage("Minji", at, "ㅋㅋ") })

        assertNotEquals(hashes[0], hashes[1])
    }

    @Test
    fun `overlapping re-import reproduces the same hashes for the shared prefix`() {
        val first = listOf(HashableMessage("Minji", at, "ㅇㅇ"), HashableMessage("Minji", at, "ㅇㅇ"))
        val reimport = first + HashableMessage("Junho", at.plusSeconds(60), "Book the ferry")

        assertEquals(hasher.hashAll(first), hasher.hashAll(reimport).take(2))
    }

    @Test
    fun `different speakers or minutes produce different hashes`() {
        val hashes = hasher.hashAll(
            listOf(
                HashableMessage("Minji", at, "OK"),
                HashableMessage("Junho", at, "OK"),
                HashableMessage("Minji", at.plusSeconds(60), "OK"),
            ),
        )

        assertEquals(3, hashes.toSet().size)
    }
}
