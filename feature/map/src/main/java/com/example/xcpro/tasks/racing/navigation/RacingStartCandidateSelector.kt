package com.example.xcpro.tasks.racing.navigation

internal object RacingStartCandidateSelector {

    fun selectBestIndex(candidates: List<RacingStartCandidate>): Int? {
        val valid = candidates.withIndex().filter { it.value.isValid }
        if (valid.isEmpty()) return null

        val comparator =
            compareBy<IndexedValue<RacingStartCandidate>>(
                { penaltyScore(it.value.penaltyFlags) },
                { candidateTypeRank(it.value.candidateType) }
            )
                .thenComparator { left, right ->
                    // Later start time is preferred when penalties/type rank are equivalent.
                    right.value.timestampMillis.compareTo(left.value.timestampMillis)
                }
                .thenBy { it.index }

        return valid.minWithOrNull(comparator)?.index
    }

    private fun candidateTypeRank(type: RacingStartCandidateType): Int {
        return when (type) {
            RacingStartCandidateType.STRICT -> 0
            RacingStartCandidateType.TOLERANCE -> 1
        }
    }

    private fun penaltyScore(flags: Set<RacingStartPenaltyFlag>): Int {
        if (flags.isEmpty()) return 0
        var total = 0
        flags.forEach { flag ->
            total += when (flag) {
                RacingStartPenaltyFlag.TOLERANCE_START -> 20
                RacingStartPenaltyFlag.PRE_START_ALTITUDE_NOT_SATISFIED -> 30
                RacingStartPenaltyFlag.MAX_START_ALTITUDE_EXCEEDED -> 40
                RacingStartPenaltyFlag.MAX_START_GROUNDSPEED_EXCEEDED -> 40
                RacingStartPenaltyFlag.PEV_MISSING -> 50
                RacingStartPenaltyFlag.PEV_OUTSIDE_WINDOW -> 50
                RacingStartPenaltyFlag.ALTITUDE_REFERENCE_FALLBACK_TO_MSL -> 10
            }
        }
        return total
    }
}
