package com.dudecide.shared.vote

class VoteScoringService {

    fun scoreDates(options: List<DateVoteCounts>): List<ScoredDateOption> =
        options
            .map {
                ScoredDateOption(
                    date = it.date,
                    rawScore = it.available * 1.0 + it.maybe * 0.5 + it.unavailable * -2.0,
                    available = it.available,
                    maybe = it.maybe,
                    unavailable = it.unavailable,
                )
            }
            .sortedWith(
                compareBy<ScoredDateOption> { it.unavailable }
                    .thenByDescending { it.rawScore }
                    .thenByDescending { it.available }
                    .thenByDescending { it.maybe }
                    .thenBy { it.date },
            )

    fun canEarlyCloseDateVote(options: List<DateVoteCounts>, unresolvedParticipants: Int): Boolean {
        require(options.isNotEmpty()) { "at least one option is required" }

        val ranked = scoreDates(options)
        val leader = ranked.first()
        val challengers = ranked.drop(1)

        return challengers.all { challenger ->
            val challengedLeader = leader.copy(
                rawScore = leader.rawScore + unresolvedParticipants * -2.0,
                unavailable = leader.unavailable + unresolvedParticipants,
            )
            val boostedChallenger = challenger.copy(
                rawScore = challenger.rawScore + unresolvedParticipants * 1.0,
                available = challenger.available + unresolvedParticipants,
            )

            compareDateOptions(challengedLeader, boostedChallenger) <= 0
        }
    }

    fun scoreLocations(options: List<LocationVoteCounts>): List<ScoredLocationOption> =
        options
            .map {
                ScoredLocationOption(
                    optionId = it.optionId,
                    rawScore = it.good * 1.0 + it.okay * 0.2 + it.dislike * -1.5,
                    good = it.good,
                    okay = it.okay,
                    dislike = it.dislike,
                    maxBudget = it.maxBudget,
                    travelMinutes = it.travelMinutes,
                )
            }
            .sortedWith(
                compareBy<ScoredLocationOption> { it.dislike }
                    .thenByDescending { it.rawScore }
                    .thenByDescending { it.good }
                    .thenBy { it.maxBudget }
                    .thenBy { it.travelMinutes },
            )

    fun canEarlyCloseLocationVote(options: List<LocationVoteCounts>, unresolvedParticipants: Int): Boolean {
        require(options.isNotEmpty()) { "at least one option is required" }

        val ranked = scoreLocations(options)
        val leader = ranked.first()
        val challengers = ranked.drop(1)

        return challengers.all { challenger ->
            val challengedLeader = leader.copy(
                rawScore = leader.rawScore + unresolvedParticipants * -1.5,
                dislike = leader.dislike + unresolvedParticipants,
            )
            val boostedChallenger = challenger.copy(
                rawScore = challenger.rawScore + unresolvedParticipants * 1.0,
                good = challenger.good + unresolvedParticipants,
            )

            compareLocationOptions(challengedLeader, boostedChallenger) <= 0
        }
    }

    private fun compareDateOptions(left: ScoredDateOption, right: ScoredDateOption): Int =
        compareValuesBy(left, right, { it.unavailable })
            .takeIf { it != 0 }
            ?: compareValuesBy(right, left, { it.rawScore })
                .takeIf { it != 0 }
            ?: compareValuesBy(right, left, { it.available })
                .takeIf { it != 0 }
            ?: compareValuesBy(right, left, { it.maybe })
                .takeIf { it != 0 }
            ?: compareValuesBy(left, right, { it.date })

    private fun compareLocationOptions(left: ScoredLocationOption, right: ScoredLocationOption): Int =
        compareValuesBy(left, right, { it.dislike })
            .takeIf { it != 0 }
            ?: compareValuesBy(right, left, { it.rawScore })
                .takeIf { it != 0 }
            ?: compareValuesBy(right, left, { it.good })
                .takeIf { it != 0 }
            ?: compareValuesBy(left, right, { it.maxBudget })
                .takeIf { it != 0 }
            ?: compareValuesBy(left, right, { it.travelMinutes })
}
