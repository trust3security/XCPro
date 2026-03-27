package com.example.xcpro.glide

import com.example.xcpro.tasks.navigation.NavigationRoutePoint
import com.example.xcpro.tasks.core.RacingAltitudeReference

enum class GlideTargetKind {
    TASK_FINISH
}

enum class GlideInvalidReason {
    NO_TASK,
    PRESTART,
    NO_FINISH_ALTITUDE,
    NO_POSITION,
    NO_ALTITUDE,
    NO_POLAR,
    INVALID_ROUTE,
    INVALID_SPEED,
    FINISHED,
    INVALID
}

enum class GlideDegradedReason {
    STILL_AIR_ASSUMED
}

data class GlideFinishConstraint(
    val requiredAltitudeMeters: Double,
    val altitudeReference: RacingAltitudeReference
)

data class GlideTargetSnapshot(
    val kind: GlideTargetKind? = null,
    val label: String = "",
    val remainingWaypoints: List<NavigationRoutePoint> = emptyList(),
    val finishConstraint: GlideFinishConstraint? = null,
    val valid: Boolean = false,
    val invalidReason: GlideInvalidReason = GlideInvalidReason.NO_TASK
)

data class GlideSolution(
    val valid: Boolean,
    val degraded: Boolean = false,
    val degradedReason: GlideDegradedReason? = null,
    val invalidReason: GlideInvalidReason? = null,
    val requiredGlideRatio: Double = Double.NaN,
    val arrivalHeightMeters: Double = Double.NaN,
    val requiredAltitudeMeters: Double = Double.NaN,
    val arrivalHeightMc0Meters: Double = Double.NaN,
    val distanceRemainingMeters: Double = Double.NaN
) {
    companion object {
        fun invalid(reason: GlideInvalidReason): GlideSolution = GlideSolution(
            valid = false,
            invalidReason = reason
        )

        fun degraded(
            reason: GlideDegradedReason,
            requiredGlideRatio: Double,
            arrivalHeightMeters: Double,
            requiredAltitudeMeters: Double,
            arrivalHeightMc0Meters: Double,
            distanceRemainingMeters: Double
        ): GlideSolution = GlideSolution(
            valid = true,
            degraded = true,
            degradedReason = reason,
            requiredGlideRatio = requiredGlideRatio,
            arrivalHeightMeters = arrivalHeightMeters,
            requiredAltitudeMeters = requiredAltitudeMeters,
            arrivalHeightMc0Meters = arrivalHeightMc0Meters,
            distanceRemainingMeters = distanceRemainingMeters
        )
    }
}
