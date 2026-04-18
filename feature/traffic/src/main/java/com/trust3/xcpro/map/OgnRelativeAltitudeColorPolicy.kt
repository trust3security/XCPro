package com.trust3.xcpro.map

import kotlin.math.abs

enum class OgnRelativeAltitudeBand {
    ABOVE,
    BELOW,
    NEAR,
    UNKNOWN
}

/**
 * Relative-altitude icon band policy for OGN gliders.
 *
 * The black band is inclusive at +/-100 ft around ownship altitude.
 */
object OgnRelativeAltitudeColorPolicy {
    private const val FEET_TO_METERS = 0.3048
    private const val BLACK_BAND_FEET = 100.0
    const val BLACK_BAND_METERS = BLACK_BAND_FEET * FEET_TO_METERS

    fun resolveBand(deltaMeters: Double?): OgnRelativeAltitudeBand {
        val delta = deltaMeters?.takeIf { it.isFinite() } ?: return OgnRelativeAltitudeBand.UNKNOWN
        if (abs(delta) <= BLACK_BAND_METERS) return OgnRelativeAltitudeBand.NEAR
        return if (delta > 0.0) OgnRelativeAltitudeBand.ABOVE else OgnRelativeAltitudeBand.BELOW
    }
}
