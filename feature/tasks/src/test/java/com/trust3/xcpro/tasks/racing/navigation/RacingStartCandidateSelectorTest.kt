package com.trust3.xcpro.tasks.racing.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RacingStartCandidateSelectorTest {

    @Test
    fun selectBestIndex_returnsNullWhenNoValidCandidates() {
        val candidates = listOf(
            RacingStartCandidate(
                timestampMillis = 1_000L,
                candidateType = RacingStartCandidateType.STRICT,
                isValid = false,
                rejectionReason = RacingStartRejectionReason.GATE_NOT_OPEN
            )
        )

        assertNull(RacingStartCandidateSelector.selectBestIndex(candidates))
    }

    @Test
    fun selectBestIndex_prefersFewerPenaltiesThenStrictThenLatestTime() {
        val candidates = listOf(
            RacingStartCandidate(
                timestampMillis = 1_000L,
                candidateType = RacingStartCandidateType.TOLERANCE,
                isValid = true,
                penaltyFlags = setOf(RacingStartPenaltyFlag.TOLERANCE_START)
            ),
            RacingStartCandidate(
                timestampMillis = 2_000L,
                candidateType = RacingStartCandidateType.STRICT,
                isValid = true,
                penaltyFlags = emptySet()
            ),
            RacingStartCandidate(
                timestampMillis = 3_000L,
                candidateType = RacingStartCandidateType.STRICT,
                isValid = true,
                penaltyFlags = setOf(RacingStartPenaltyFlag.PEV_MISSING)
            )
        )

        assertEquals(1, RacingStartCandidateSelector.selectBestIndex(candidates))
    }

    @Test
    fun selectBestIndex_usesLatestTimestampAsTieBreaker() {
        val candidates = listOf(
            RacingStartCandidate(
                timestampMillis = 2_000L,
                candidateType = RacingStartCandidateType.STRICT,
                isValid = true,
                penaltyFlags = emptySet()
            ),
            RacingStartCandidate(
                timestampMillis = 3_000L,
                candidateType = RacingStartCandidateType.STRICT,
                isValid = true,
                penaltyFlags = emptySet()
            )
        )

        assertEquals(1, RacingStartCandidateSelector.selectBestIndex(candidates))
    }
}
