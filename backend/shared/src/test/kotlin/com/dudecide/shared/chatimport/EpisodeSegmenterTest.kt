package com.dudecide.shared.chatimport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class EpisodeSegmenterTest {

    private val segmenter = EpisodeSegmenter()

    @Test
    fun `empty input yields no episodes`() {
        assertEquals(emptyList<Episode>(), segmenter.segment(PrefilterResult(emptyList(), emptyList())))
    }

    @Test
    fun `gap of three hours starts a new episode`() {
        val messages = burst("a", "2026-10-02T19:00:00+09:00", 6) + burst("b", "2026-10-02T23:00:00+09:00", 6)

        val episodes = segmenter.segment(PrefilterResult(messages, emptyList()))

        assertEquals(listOf(6, 6), episodes.map { it.messages.size })
        assertEquals(listOf(0, 1), episodes.map { it.index })
    }

    @Test
    fun `day change splits only when the gap is at least one hour`() {
        val continuous = burst("a", "2026-10-02T23:50:00+09:00", 6) + burst("b", "2026-10-03T00:05:00+09:00", 6)
        val separated = burst("a", "2026-10-02T23:00:00+09:00", 6) + burst("b", "2026-10-03T01:00:00+09:00", 6)

        assertEquals(1, segmenter.segment(PrefilterResult(continuous, emptyList())).size)
        assertEquals(2, segmenter.segment(PrefilterResult(separated, emptyList())).size)
    }

    @Test
    fun `small episode merges into the nearer neighbor`() {
        val messages = burst("a", "2026-10-02T10:00:00+09:00", 6) +
            burst("b", "2026-10-02T16:00:00+09:00", 2) +
            burst("c", "2026-10-02T20:00:00+09:00", 6)

        val episodes = segmenter.segment(PrefilterResult(messages, emptyList()))

        assertEquals(listOf(6, 8), episodes.map { it.messages.size })
        assertEquals("b0", episodes[1].messages.first().id)
    }

    @Test
    fun `oversized episode splits at the largest internal gap`() {
        val messages = burst("a", "2026-10-02T10:00:00+09:00", 100) +
            burst("b", "2026-10-02T11:50:00+09:00", 100)

        val episodes = segmenter.segment(PrefilterResult(messages, emptyList()))

        assertEquals(listOf(100, 100), episodes.map { it.messages.size })
    }

    @Test
    fun `split keeps both sides at minimum size`() {
        val config = SegmentationConfig(maxEpisodeSize = 10, minEpisodeSize = 5)
        val messages = burst("a", "2026-10-02T10:00:00+09:00", 11) + burst("b", "2026-10-02T11:00:00+09:00", 1)

        val episodes = EpisodeSegmenter(config).segment(PrefilterResult(messages, emptyList()))

        assertTrue(episodes.all { it.messages.size in 5..10 }, episodes.map { it.messages.size }.toString())
        assertEquals(12, episodes.sumOf { it.messages.size })
    }

    @Test
    fun `dropped messages are counted on the episode they fall into`() {
        val kept = burst("a", "2026-10-02T10:00:00+09:00", 6) + burst("b", "2026-10-02T20:00:00+09:00", 6)
        val dropped = listOf(
            message("d0", "2026-10-02T09:00:00+09:00"),
            message("d1", "2026-10-02T10:02:00+09:00"),
            message("d2", "2026-10-02T21:00:00+09:00"),
        )

        val episodes = segmenter.segment(PrefilterResult(kept, dropped))

        assertEquals(listOf(2, 1), episodes.map { it.filteredCount })
    }

    @Test
    fun `unsorted input is ordered by sent time`() {
        val messages = burst("a", "2026-10-02T10:00:00+09:00", 6).reversed()

        val episode = segmenter.segment(PrefilterResult(messages, emptyList())).single()

        assertEquals((0 until 6).map { "a$it" }, episode.messages.map { it.id })
    }

    private fun burst(prefix: String, start: String, count: Int): List<ImportedMessage> {
        val startAt = Instant.from(java.time.OffsetDateTime.parse(start))
        return (0 until count).map { ImportedMessage("$prefix$it", "A", startAt.plusSeconds(it * 30L), "message $it") }
    }

    private fun message(id: String, sentAt: String) =
        ImportedMessage(id, "A", Instant.from(java.time.OffsetDateTime.parse(sentAt)), "ㅋㅋ")
}
