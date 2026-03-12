package com.example.xcpro.map.model

/**
 * UI-safe location snapshot derived from GPS data.
 * Uses primitives to keep UI free of sensor/data-layer types.
 */
data class MapLocationUiModel(
    val latitude: Double,
    val longitude: Double,
    val speedMs: Double,
    val bearingDeg: Double,
    val accuracyMeters: Double,
    val bearingAccuracyDeg: Double? = null,
    val speedAccuracyMs: Double? = null,
    val timestampMs: Long,
    val monotonicTimestampMs: Long = 0L
) {
    val timeForCalculationsMillis: Long
        get() = if (monotonicTimestampMs > 0L) monotonicTimestampMs else timestampMs
}

sealed class GpsStatusUiModel {
    object NoPermission : GpsStatusUiModel()
    object Disabled : GpsStatusUiModel()
    object Searching : GpsStatusUiModel()
    data class LostFix(val ageMs: Long) : GpsStatusUiModel()
    data class Ok(val ageMs: Long, val accuracyMeters: Float?) : GpsStatusUiModel()
}
