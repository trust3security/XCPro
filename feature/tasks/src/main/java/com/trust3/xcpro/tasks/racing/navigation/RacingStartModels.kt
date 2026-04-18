package com.trust3.xcpro.tasks.racing.navigation

import com.trust3.xcpro.tasks.core.RacingAltitudeReference

enum class RacingStartCandidateType {
    STRICT,
    TOLERANCE
}

enum class RacingStartRejectionReason {
    GATE_NOT_OPEN,
    GATE_CLOSED,
    WRONG_DIRECTION
}

enum class RacingStartPenaltyFlag {
    TOLERANCE_START,
    PRE_START_ALTITUDE_NOT_SATISFIED,
    PEV_MISSING,
    PEV_OUTSIDE_WINDOW,
    ALTITUDE_REFERENCE_FALLBACK_TO_MSL,
    MAX_START_ALTITUDE_EXCEEDED,
    MAX_START_GROUNDSPEED_EXCEEDED
}

data class RacingStartCandidate(
    val timestampMillis: Long,
    val candidateType: RacingStartCandidateType,
    val isValid: Boolean,
    val rejectionReason: RacingStartRejectionReason? = null,
    val penaltyFlags: Set<RacingStartPenaltyFlag> = emptySet(),
    val sampleFix: RacingNavigationFix? = null,
    val altitudeReference: RacingAltitudeReference? = null,
    val crossingEvidence: RacingBoundaryCrossingEvidence? = null
) {
    val hasPenalty: Boolean
        get() = penaltyFlags.isNotEmpty()
}
