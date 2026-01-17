package com.example.xcpro.tasks.racing.boundary

data class RacingBoundaryPoint(
    val lat: Double,
    val lon: Double
)

enum class RacingBoundaryTransition {
    ENTER,
    EXIT
}

data class RacingBoundaryCrossing(
    val transition: RacingBoundaryTransition,
    val crossingPoint: RacingBoundaryPoint,
    val crossingTimeMillis: Long,
    val insideAnchor: RacingBoundaryPoint,
    val outsideAnchor: RacingBoundaryPoint
)
