package com.example.xcpro.sensors.domain

import com.example.xcpro.weather.wind.model.WindVector
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Helper that encapsulates wind-based airspeed estimation.
 */
class WindEstimator {

    internal fun fromWind(
        gpsSpeed: Double,
        gpsBearingDeg: Double,
        altitudeMeters: Double,
        qnhHpa: Double,
        windVector: WindVector?
    ): AirspeedEstimate? {
        if (windVector == null || !gpsSpeed.isFinite() || gpsSpeed <= 0.1) return null
        if (!gpsBearingDeg.isFinite()) return null

        val bearingRad = Math.toRadians(gpsBearingDeg)
        val groundEast = gpsSpeed * sin(bearingRad)
        val groundNorth = gpsSpeed * cos(bearingRad)
        // WindVector represents the velocity of the airmass ("wind TO"), so:
        //   ground = air + wind_to  =>  air = ground - wind_to
        val airEast = groundEast - windVector.east
        val airNorth = groundNorth - windVector.north
        val tas = hypot(airEast, airNorth)
        if (!tas.isFinite() || tas <= 0.1) return null

        val densityRatio = computeDensityRatio(altitudeMeters, qnhHpa)
        val indicated = if (densityRatio > 0.0) tas * sqrt(densityRatio) else tas
        return AirspeedEstimate(indicatedMs = indicated, trueMs = tas, source = AirspeedSource.WIND_VECTOR)
    }
}
