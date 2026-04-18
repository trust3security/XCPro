package com.trust3.xcpro.navigation

enum class WaypointEtaSource {
    NONE,
    GROUND_SPEED
}

enum class WaypointNavigationInvalidReason {
    NONE,
    NO_TASK,
    PRESTART,
    FINISHED,
    INVALID_ROUTE,
    INVALID,
    NO_POSITION,
    STATIC,
    NO_TIME
}

data class WaypointNavigationSnapshot(
    val targetLabel: String = "",
    val distanceMeters: Double = Double.NaN,
    val bearingTrueDegrees: Double = Double.NaN,
    val valid: Boolean = false,
    val invalidReason: WaypointNavigationInvalidReason = WaypointNavigationInvalidReason.NO_TASK,
    val etaEpochMillis: Long = 0L,
    val etaValid: Boolean = false,
    val etaSource: WaypointEtaSource = WaypointEtaSource.NONE,
    val etaInvalidReason: WaypointNavigationInvalidReason = WaypointNavigationInvalidReason.NO_TASK
)
