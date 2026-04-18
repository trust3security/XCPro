package com.trust3.xcpro.map

import kotlin.math.abs

internal object OgnGliderCloseProximityColorPolicy {
    private const val FEET_TO_METERS = 0.3048

    const val CLOSE_DISTANCE_METERS = 1_000.0
    const val CLOSE_VERTICAL_METERS = 300.0 * FEET_TO_METERS

    fun shouldUseRed(distanceMeters: Double?, deltaMeters: Double?): Boolean {
        val distance = distanceMeters?.takeIf { it.isFinite() && it >= 0.0 } ?: return false
        val delta = deltaMeters?.takeIf { it.isFinite() } ?: return false
        return distance <= CLOSE_DISTANCE_METERS && abs(delta) <= CLOSE_VERTICAL_METERS
    }
}
