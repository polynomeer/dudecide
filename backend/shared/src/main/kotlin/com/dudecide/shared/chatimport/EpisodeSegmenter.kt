package com.dudecide.shared.chatimport

import java.time.Duration

/**
 * Splits prefiltered chat history into conversation episodes for per-episode LLM extraction.
 * Order: split on time boundaries, merge tiny episodes into the nearer neighbor, then split oversized ones.
 */
class EpisodeSegmenter(private val config: SegmentationConfig = SegmentationConfig()) {

    fun segment(prefiltered: PrefilterResult): List<Episode> {
        val messages = prefiltered.kept.sortedBy { it.sentAt }
        if (messages.isEmpty()) return emptyList()

        val groups = mergeSmall(splitOnBoundaries(messages)).flatMap { splitLarge(it) }
        val filteredCounts = countFiltered(groups, prefiltered.dropped)

        return groups.mapIndexed { index, group ->
            Episode(index = index, messages = group, filteredCount = filteredCounts[index])
        }
    }

    private fun splitOnBoundaries(messages: List<ImportedMessage>): List<List<ImportedMessage>> {
        val groups = mutableListOf(mutableListOf(messages.first()))
        messages.zipWithNext().forEach { (previous, next) ->
            if (isBoundary(previous, next)) groups += mutableListOf(next) else groups.last() += next
        }
        return groups
    }

    private fun isBoundary(previous: ImportedMessage, next: ImportedMessage): Boolean {
        val gap = Duration.between(previous.sentAt, next.sentAt)
        if (gap >= config.hardGap) return true

        val dayChanged = previous.sentAt.atZone(config.zone).toLocalDate() != next.sentAt.atZone(config.zone).toLocalDate()
        return dayChanged && gap >= config.dayChangeGap
    }

    private fun mergeSmall(initial: List<List<ImportedMessage>>): List<List<ImportedMessage>> {
        val groups = initial.toMutableList()
        while (groups.size > 1) {
            val index = groups.indexOfFirst { it.size < config.minEpisodeSize }
            if (index < 0) break

            val group = groups[index]
            val previousGap = groups.getOrNull(index - 1)?.let { Duration.between(it.last().sentAt, group.first().sentAt) }
            val nextGap = groups.getOrNull(index + 1)?.let { Duration.between(group.last().sentAt, it.first().sentAt) }
            val mergeIntoPrevious = nextGap == null || (previousGap != null && previousGap <= nextGap)

            if (mergeIntoPrevious) {
                groups[index - 1] = groups[index - 1] + group
            } else {
                groups[index + 1] = group + groups[index + 1]
            }
            groups.removeAt(index)
        }
        return groups
    }

    private fun splitLarge(group: List<ImportedMessage>): List<List<ImportedMessage>> {
        if (group.size <= config.maxEpisodeSize) return listOf(group)

        val splitAt = (config.minEpisodeSize..group.size - config.minEpisodeSize)
            .maxBy { Duration.between(group[it - 1].sentAt, group[it].sentAt) }

        return splitLarge(group.subList(0, splitAt)) + splitLarge(group.subList(splitAt, group.size))
    }

    private fun countFiltered(groups: List<List<ImportedMessage>>, dropped: List<ImportedMessage>): IntArray {
        val counts = IntArray(groups.size)
        dropped.forEach { message ->
            val owner = groups.indexOfLast { it.first().sentAt <= message.sentAt }.coerceAtLeast(0)
            counts[owner]++
        }
        return counts
    }
}
