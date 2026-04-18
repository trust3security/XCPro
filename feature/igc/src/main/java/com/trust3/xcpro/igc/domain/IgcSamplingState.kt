package com.trust3.xcpro.igc.domain

/**
 * In-memory mapper state for live B-record generation continuity.
 */
data class IgcSamplingState(
    val lastEmissionWallTimeMs: Long? = null,
    val lastValidPosition: Position? = null,
    val lastValidPressureAltitudeMeters: Int? = null
) {
    data class Position(
        val latitudeDegrees: Double,
        val longitudeDegrees: Double
    )
}
