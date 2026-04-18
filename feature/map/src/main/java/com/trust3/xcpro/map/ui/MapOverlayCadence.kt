package com.trust3.xcpro.map.ui

import kotlin.math.round

internal fun quantizeOverlayOwnshipAltitudeMeters(
    altitudeMeters: Double?,
    quantizeStepMeters: Double = OVERLAY_OWNSHIP_ALTITUDE_QUANTIZE_STEP_METERS
): Double? {
    val altitude = altitudeMeters?.takeIf { it.isFinite() } ?: return null
    if (!quantizeStepMeters.isFinite() || quantizeStepMeters <= 0.0) return altitude
    return round(altitude / quantizeStepMeters) * quantizeStepMeters
}

private const val OVERLAY_OWNSHIP_ALTITUDE_QUANTIZE_STEP_METERS = 2.0
