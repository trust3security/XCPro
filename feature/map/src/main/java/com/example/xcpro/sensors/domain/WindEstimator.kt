package com.example.xcpro.sensors.domain

import com.example.xcpro.weather.wind.model.WindVector
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Helper that encapsulates wind-based airspeed estimation.
 */
internal class WindEstimator {

    fun fromWind(
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

    private fun computeDensityRatio(altitudeMeters: Double, qnhHpa: Double): Double {
        val tempSeaLevelK = FlightMetricsConstants.SEA_LEVEL_TEMP_CELSIUS + 273.15
        val theta = 1.0 + (FlightMetricsConstants.TEMP_LAPSE_RATE_C_PER_M * altitudeMeters) / tempSeaLevelK
        if (theta <= 0.0) return 0.0
        val exponent = (-FlightMetricsConstants.GRAVITY / (FlightMetricsConstants.GAS_CONSTANT * FlightMetricsConstants.TEMP_LAPSE_RATE_C_PER_M)) - 1.0
        val standardDensityRatio = theta.pow(exponent)
        val qnhRatio = (qnhHpa / FlightMetricsConstants.SEA_LEVEL_PRESSURE_HPA)
            .takeIf { it.isFinite() && it > 0.0 }
            ?: 1.0
        return standardDensityRatio * qnhRatio
    }
}
