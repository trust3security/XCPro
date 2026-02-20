package com.example.xcpro.ogn

enum class OgnThermalHotspotState {
    ACTIVE,
    FINALIZED
}

data class OgnThermalHotspot(
    val id: String,
    val sourceTargetId: String,
    val sourceLabel: String,
    val latitude: Double,
    val longitude: Double,
    val startedAtMonoMs: Long,
    val updatedAtMonoMs: Long,
    val startAltitudeMeters: Double?,
    val maxAltitudeMeters: Double?,
    val maxAltitudeAtMonoMs: Long?,
    val maxClimbRateMps: Double,
    val averageClimbRateMps: Double?,
    val averageBottomToTopClimbRateMps: Double?,
    val snailColorIndex: Int,
    val state: OgnThermalHotspotState
)
