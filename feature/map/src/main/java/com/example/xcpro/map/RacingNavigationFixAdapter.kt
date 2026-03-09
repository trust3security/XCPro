package com.example.xcpro.map

import com.example.xcpro.sensors.GPSData
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix

/**
 * Single-source adapter for mapping GPSData to RacingNavigationFix.
 * Enforces the time base used for navigation decisions.
 */
object RacingNavigationFixAdapter {
    fun toFix(gps: GPSData): RacingNavigationFix = RacingNavigationFix(
        lat = gps.latitude,
        lon = gps.longitude,
        timestampMillis = gps.timeForCalculationsMillis,
        accuracyMeters = gps.accuracy.toDouble(),
        altitudeMslMeters = gps.altitude.value,
        groundSpeedMs = gps.speed.value,
        bearingDeg = gps.bearing
    )

    fun toFix(data: CompleteFlightData): RacingNavigationFix? {
        val gps = data.gps ?: return null
        return RacingNavigationFix(
            lat = gps.latitude,
            lon = gps.longitude,
            timestampMillis = gps.timeForCalculationsMillis,
            accuracyMeters = gps.accuracy.toDouble(),
            altitudeMslMeters = gps.altitude.value,
            altitudeQnhMeters = data.baroAltitude.value,
            groundSpeedMs = gps.speed.value,
            bearingDeg = gps.bearing
        )
    }
}
