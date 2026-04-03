package com.example.xcpro.ogn

enum class OgnThermalHotspotState {
    ACTIVE,
    FINALIZED
}

// Hotspot coordinates are the stable display anchor for the thermal, not a running centroid.
data class OgnThermalHotspot(
    val id: String,
    val sourceTargetId: String,
    val sourceLabel: String,
    val latitude: Double,
    val longitude: Double,
    val startedAtMonoMs: Long,
    val startedAtWallMs: Long,
    val updatedAtMonoMs: Long,
    val updatedAtWallMs: Long,
    val startAltitudeMeters: Double?,
    val maxAltitudeMeters: Double?,
    val maxAltitudeAtMonoMs: Long?,
    val maxClimbRateMps: Double,
    val averageClimbRateMps: Double?,
    val averageBottomToTopClimbRateMps: Double?,
    val snailColorIndex: Int,
    val state: OgnThermalHotspotState
)

internal fun OgnThermalHotspot.displayClimbRateMps(): Double? {
    return averageBottomToTopClimbRateMps
        ?.takeIf { it.isFinite() }
        ?: averageClimbRateMps?.takeIf { it.isFinite() }
}
