package com.trust3.xcpro.replay

/**
 * Stabilizes replay heading when movement is too small to compute a reliable bearing.
 */
class ReplayHeadingResolver(
    private val minSpeedMs: Double = DEFAULT_MIN_SPEED_MS,
    private val minDistanceM: Double = DEFAULT_MIN_DISTANCE_M
) {
    private var lastHeadingDeg: Float? = null

    fun resolve(movement: MovementSnapshot): Float {
        val derivedHeading = movement.bearingDeg
        val previousHeading = lastHeadingDeg
        val shouldReusePrevious = movement.distanceMeters < minDistanceM || movement.speedMs < minSpeedMs

        val nextHeading = when {
            previousHeading == null -> derivedHeading
            shouldReusePrevious -> previousHeading
            // Preserve raw turn-rate so circling/wind detectors behave like live GPS.
            // Camera/icon smoothing happens downstream (MapPositionController bearing clamp).
            else -> derivedHeading
        }

        lastHeadingDeg = nextHeading
        return nextHeading
    }

    fun reset() {
        lastHeadingDeg = null
    }

    companion object {
        private const val DEFAULT_MIN_SPEED_MS = 1.0
        private const val DEFAULT_MIN_DISTANCE_M = 3.0
    }
}
