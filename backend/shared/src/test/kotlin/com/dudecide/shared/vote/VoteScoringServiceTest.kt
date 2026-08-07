package com.dudecide.shared.vote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VoteScoringServiceTest {

    private val service = VoteScoringService()

    @Test
    fun `date scoring prefers zero conflict option`() {
        val ranked = service.scoreDates(
            listOf(
                DateVoteCounts(LocalDate.of(2026, 7, 18), available = 4, maybe = 1, unavailable = 0),
                DateVoteCounts(LocalDate.of(2026, 7, 19), available = 5, maybe = 0, unavailable = 1),
            ),
        )

        assertEquals(LocalDate.of(2026, 7, 18), ranked.first().date)
        assertEquals(4.5, ranked.first().rawScore)
    }

    @Test
    fun `date early close returns true for documented conservative example`() {
        val canClose = service.canEarlyCloseDateVote(
            listOf(
                DateVoteCounts(LocalDate.of(2026, 7, 18), available = 7, maybe = 1, unavailable = 0),
                DateVoteCounts(LocalDate.of(2026, 7, 25), available = 5, maybe = 2, unavailable = 1),
            ),
            unresolvedParticipants = 1,
        )

        assertTrue(canClose)
    }

    @Test
    fun `location scoring prefers lower opposition package`() {
        val ranked = service.scoreLocations(
            listOf(
                LocationVoteCounts("A", good = 4, okay = 3, dislike = 1, maxBudget = 220000, travelMinutes = 120),
                LocationVoteCounts("B", good = 5, okay = 0, dislike = 3, maxBudget = 180000, travelMinutes = 90),
            ),
        )

        assertEquals("A", ranked.first().optionId)
        assertEquals(3.1, ranked.first().rawScore)
    }

    @Test
    fun `location early close returns true when challenger cannot catch up`() {
        val canClose = service.canEarlyCloseLocationVote(
            listOf(
                LocationVoteCounts("A", good = 6, okay = 1, dislike = 0, maxBudget = 220000, travelMinutes = 120),
                LocationVoteCounts("B", good = 4, okay = 1, dislike = 2, maxBudget = 180000, travelMinutes = 90),
            ),
            unresolvedParticipants = 1,
        )

        assertTrue(canClose)
    }
}
