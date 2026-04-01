package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.racing.boundary.RacingBoundaryPoint
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryCrossing
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryEvidenceSource
import com.example.xcpro.tasks.racing.models.RacingWaypointRole

enum class RacingNavigationEventType {
    START,
    START_REJECTED,
    TURNPOINT,
    TURNPOINT_NEAR_MISS,
    FINISH
}

enum class RacingFinishOutcome {
    VALID,
    LANDING_PENDING,
    LANDED_WITHOUT_DELAY,
    LANDING_DELAY_VIOLATION,
    OUTLANDED_AT_CLOSE,
    CONTEST_BOUNDARY_STOP_PLUS_FIVE
}

data class RacingBoundaryCrossingEvidence(
    val crossingPoint: RacingBoundaryPoint,
    val insideAnchor: RacingBoundaryPoint,
    val outsideAnchor: RacingBoundaryPoint,
    val evidenceSource: RacingBoundaryEvidenceSource
)

data class RacingNavigationEvent(
    val type: RacingNavigationEventType,
    val fromLegIndex: Int,
    val toLegIndex: Int,
    val waypointRole: RacingWaypointRole,
    val timestampMillis: Long,
    val startCandidate: RacingStartCandidate? = null,
    val turnpointNearMissDistanceMeters: Double? = null,
    val finishOutcome: RacingFinishOutcome? = null,
    val finishUsedStraightInException: Boolean = false,
    val crossingEvidence: RacingBoundaryCrossingEvidence? = null
)

internal fun RacingBoundaryCrossing.toEventEvidence(): RacingBoundaryCrossingEvidence =
    RacingBoundaryCrossingEvidence(
        crossingPoint = crossingPoint,
        insideAnchor = insideAnchor,
        outsideAnchor = outsideAnchor,
        evidenceSource = evidenceSource
    )
