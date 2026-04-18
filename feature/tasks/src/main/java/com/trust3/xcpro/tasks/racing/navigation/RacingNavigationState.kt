package com.trust3.xcpro.tasks.racing.navigation

import com.trust3.xcpro.tasks.core.RacingAltitudeReference
import com.trust3.xcpro.tasks.racing.models.RacingWaypointRole

enum class RacingNavigationStatus {
    PENDING_START,
    STARTED,
    IN_PROGRESS,
    FINISHED,
    INVALIDATED
}

data class RacingNavigationFix(
    val lat: Double,
    val lon: Double,
    val timestampMillis: Long,
    val accuracyMeters: Double? = null,
    val altitudeMslMeters: Double? = null,
    val altitudeQnhMeters: Double? = null,
    val groundSpeedMs: Double? = null,
    val bearingDeg: Double? = null
)

data class RacingCreditedBoundaryHit(
    val legIndex: Int,
    val waypointRole: RacingWaypointRole,
    val timestampMillis: Long,
    val crossingEvidence: RacingBoundaryCrossingEvidence,
    val altitudeSourceFix: RacingNavigationFix? = null,
    val altitudeReference: RacingAltitudeReference? = null
)

data class RacingNavigationState(
    val status: RacingNavigationStatus = RacingNavigationStatus.PENDING_START,
    val currentLegIndex: Int = 0,
    val lastFix: RacingNavigationFix? = null,
    val lastTransitionTimeMillis: Long = 0L,
    val taskSignature: String = "",
    val startCandidates: List<RacingStartCandidate> = emptyList(),
    val selectedStartCandidateIndex: Int? = null,
    val creditedStart: RacingCreditedBoundaryHit? = null,
    val creditedTurnpointsByLeg: Map<Int, RacingCreditedBoundaryHit> = emptyMap(),
    val creditedFinish: RacingCreditedBoundaryHit? = null,
    val preStartAltitudeSatisfied: Boolean = false,
    val hasObservedRequiredApproachSideForActiveLeg: Boolean = false,
    val reportedNearMissTurnpointLegIndices: Set<Int> = emptySet(),
    val finishOutcome: RacingFinishOutcome? = null,
    val finishUsedStraightInException: Boolean = false,
    val finishCrossingTimeMillis: Long? = null,
    val finishLandingDeadlineMillis: Long? = null,
    val finishLandingStopStartTimeMillis: Long? = null,
    val finishBoundaryStopStartTimeMillis: Long? = null,
    val lastFixBeforeFinishClose: RacingNavigationFix? = null
)

data class RacingNavigationDecision(
    val state: RacingNavigationState,
    val event: RacingNavigationEvent?
)
