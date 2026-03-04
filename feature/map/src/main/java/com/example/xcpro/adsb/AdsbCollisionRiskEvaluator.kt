package com.example.xcpro.adsb

import kotlin.math.abs

internal class AdsbCollisionRiskEvaluator(
    private val emergencyDistanceMeters: Double = EMERGENCY_DISTANCE_METERS,
    private val collisionHeadingToleranceDeg: Double = COLLISION_HEADING_TOLERANCE_DEG,
    private val emergencyMaxAgeSec: Int = EMERGENCY_MAX_AGE_SEC
) {

    fun evaluate(
        distanceMeters: Double,
        trackDeg: Double?,
        bearingDegFromUser: Double,
        altitudeDeltaMeters: Double?,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double,
        hasOwnshipReference: Boolean,
        isClosing: Boolean,
        ageSec: Int
    ): Boolean {
        if (!hasOwnshipReference) return false
        if (!isClosing) return false
        if (ageSec > emergencyMaxAgeSec) return false
        if (!distanceMeters.isFinite() || distanceMeters > emergencyDistanceMeters) return false

        val altitudeDelta = altitudeDeltaMeters ?: return false
        val above = altitudeDelta
        val below = -altitudeDelta
        if (above > verticalAboveMeters || below > verticalBelowMeters) return false

        val track = trackDeg ?: return false
        if (!track.isFinite() || !bearingDegFromUser.isFinite()) return false
        val bearingFromTargetToUser = normalizeDegrees(bearingDegFromUser + 180.0)
        val headingError = minHeadingDiffDeg(track, bearingFromTargetToUser)
        return headingError <= collisionHeadingToleranceDeg
    }

    private fun normalizeDegrees(value: Double): Double {
        val normalized = value % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }

    private fun minHeadingDiffDeg(a: Double, b: Double): Double {
        val diff = abs(normalizeDegrees(a) - normalizeDegrees(b))
        return if (diff > 180.0) 360.0 - diff else diff
    }

    private companion object {
        private const val EMERGENCY_DISTANCE_METERS = 1_000.0
        private const val COLLISION_HEADING_TOLERANCE_DEG = 20.0
        private const val EMERGENCY_MAX_AGE_SEC = 20
    }
}
