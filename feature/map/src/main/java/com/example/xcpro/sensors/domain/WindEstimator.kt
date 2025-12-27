package com.example.xcpro.sensors.domain

import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.weather.wind.model.WindVector
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Helper that encapsulates wind-based and polar-based airspeed estimation.
 * Mirrors the previous top-level functions but keeps the logic in one place.
 */
internal class WindEstimator(
    private val sinkProvider: StillAirSinkProvider? = null
    // sinkProvider is only needed for polar-based estimation; wind-only calls don't require it.
) {

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

    fun fromPolarSink(
        netto: Float,
        verticalSpeed: Double,
        altitudeMeters: Double,
        qnhHpa: Double
    ): AirspeedEstimate? {
        val provider = sinkProvider ?: return null
        val sinkEstimate = abs(netto.toDouble() - verticalSpeed)
        if (!sinkEstimate.isFinite() || sinkEstimate < FlightMetricsConstants.MIN_SINK_FOR_IAS_MS) return null

        val tasMs = findSpeedForSink(sinkEstimate, provider) ?: return null
        val densityRatio = computeDensityRatio(altitudeMeters, qnhHpa)
        val indicatedMs = if (densityRatio > 0.0) tasMs * sqrt(densityRatio) else tasMs
        return AirspeedEstimate(indicatedMs = indicatedMs, trueMs = tasMs, source = AirspeedSource.POLAR_SINK)
    }

    private fun findSpeedForSink(targetSinkMs: Double, sinkProvider: StillAirSinkProvider): Double? {
        var speed = FlightMetricsConstants.IAS_SCAN_MIN_MS
        var bestSpeed: Double? = null
        var bestError = Double.POSITIVE_INFINITY
        while (speed <= FlightMetricsConstants.IAS_SCAN_MAX_MS) {
            val sink = sinkProvider.sinkAtSpeed(speed) ?: break
            val error = abs(sink - targetSinkMs)
            if (error < bestError) {
                bestError = error
                bestSpeed = speed
            }
            speed += FlightMetricsConstants.IAS_SCAN_STEP_MS
        }
        return bestSpeed
    }

    private fun computeDensityRatio(altitudeMeters: Double, qnhHpa: Double): Double {
        val tempSeaLevelK = FlightMetricsConstants.SEA_LEVEL_TEMP_CELSIUS + 273.15
        val theta = 1.0 + (FlightMetricsConstants.TEMP_LAPSE_RATE_C_PER_M * altitudeMeters) / tempSeaLevelK
        if (theta <= 0.0) return 0.0
        val exponent = (-FlightMetricsConstants.GRAVITY / (FlightMetricsConstants.GAS_CONSTANT * FlightMetricsConstants.TEMP_LAPSE_RATE_C_PER_M)) - 1.0
        return theta.pow(exponent)
    }
}
