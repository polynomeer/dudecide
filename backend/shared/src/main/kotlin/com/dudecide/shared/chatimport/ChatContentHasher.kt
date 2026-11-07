package com.dudecide.shared.chatimport

import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.temporal.ChronoUnit

data class HashableMessage(
    val speakerDisplayName: String,
    val sentAt: Instant,
    val body: String,
)

/**
 * Content hash used to deduplicate overlapping re-imports. Exports carry minute precision only, so identical
 * `(speaker, minute, body)` tuples in one upload are told apart by their occurrence index.
 */
class ChatContentHasher {

    fun hashAll(messages: List<HashableMessage>): List<String> {
        val occurrences = mutableMapOf<String, Int>()
        return messages.map { message ->
            val key = listOf(
                normalize(message.speakerDisplayName),
                message.sentAt.truncatedTo(ChronoUnit.MINUTES).toString(),
                normalize(message.body),
            ).joinToString("\n")
            val occurrence = occurrences.merge(key, 1, Int::plus)!! - 1
            sha256("$key\n$occurrence")
        }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFC).trim().replace(WHITESPACE, " ")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
