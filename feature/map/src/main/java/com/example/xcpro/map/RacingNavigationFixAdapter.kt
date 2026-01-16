package com.example.xcpro.map

import com.example.xcpro.sensors.GPSData
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix

/**
 * Single-source adapter for mapping GPSData to RacingNavigationFix.
 * Enforces the time base used for navigation decisions.
 */
object RacingNavigationFixAdapter {
    fun toFix(gps: GPSData): RacingNavigationFix = RacingNavigationFix(
        lat = gps.latitude,
        lon = gps.longitude,
        timestampMillis = gps.timeForCalculationsMillis
    )
}
