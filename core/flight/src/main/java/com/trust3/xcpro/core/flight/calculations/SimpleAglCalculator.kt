package com.trust3.xcpro.core.flight.calculations

import com.trust3.xcpro.core.common.logging.AppLogger

class SimpleAglCalculator(
    private val terrainElevationReadPort: TerrainElevationReadPort
) {
    companion object {
        private const val TAG = "SimpleAGL"
    }

    private inline fun debug(message: () -> String) {
        AppLogger.d(TAG, message())
    }

    suspend fun calculateAgl(
        altitude: Double,
        lat: Double,
        lon: Double,
        speed: Double? = null
    ): Double? {
        val terrain = terrainElevationReadPort.getElevationMeters(lat, lon) ?: run {
            debug { "No terrain data available for AGL calculation" }
            return null
        }

        val agl = altitude - terrain
        if (agl < -50.0) {
            AppLogger.w(TAG, "AGL very negative (${agl.toInt()}m) - verify QNH calibration")
        }

        debug {
            val speedLabel = speed?.toInt()?.toString() ?: "?"
            "AGL=${agl.toInt()}m (alt=${altitude.toInt()}m, terrain=${terrain.toInt()}m, speed=${speedLabel}m/s)"
        }

        return agl
    }

    @Deprecated("Use formatAglWithStatus for better ground detection")
    fun formatAgl(agl: Double?): String = when {
        agl == null -> "---"
        agl < 5.0 -> "0"
        else -> "${agl.toInt()}"
    }

    fun formatAglWithStatus(agl: Double?, speed: Double?): Pair<String, String> = when {
        agl == null -> "---" to "NO DATA"
        agl < 5.0 && (speed == null || speed < 2.0) -> "0" to "ON GROUND"
        agl < 5.0 -> "${agl.toInt()}" to "LOW!"
        else -> "${agl.toInt()}" to "AGL"
    }
}
