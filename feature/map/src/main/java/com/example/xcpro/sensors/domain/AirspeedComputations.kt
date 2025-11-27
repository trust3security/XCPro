package com.example.xcpro.sensors.domain

import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.domain.AirspeedEstimate
import com.example.xcpro.sensors.domain.AirspeedSource
import com.example.xcpro.sensors.domain.FlightMetricsConstants.GRAVITY
import com.example.xcpro.sensors.domain.FlightMetricsConstants.IAS_SCAN_MAX_MS
import com.example.xcpro.sensors.domain.FlightMetricsConstants.IAS_SCAN_MIN_MS
import com.example.xcpro.sensors.domain.FlightMetricsConstants.IAS_SCAN_STEP_MS
import com.example.xcpro.sensors.domain.FlightMetricsConstants.MIN_SINK_FOR_IAS_MS
import com.example.xcpro.sensors.domain.FlightMetricsConstants.SEA_LEVEL_TEMP_CELSIUS
import com.example.xcpro.sensors.domain.FlightMetricsConstants.TEMP_LAPSE_RATE_C_PER_M
import com.example.xcpro.sensors.domain.FlightMetricsConstants.GAS_CONSTANT
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import com.example.xcpro.weather.wind.model.WindVector

internal fun estimateFromWind(
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
    val tasEast = groundEast + windVector.east
    val tasNorth = groundNorth + windVector.north
    val tas = hypot(tasEast, tasNorth)
    if (!tas.isFinite() || tas <= 0.1) return null
    val densityRatio = computeDensityRatio(altitudeMeters, qnhHpa)
    val indicated = if (densityRatio > 0.0) tas * sqrt(densityRatio) else tas
    return AirspeedEstimate(indicatedMs = indicated, trueMs = tas, source = AirspeedSource.WIND_VECTOR)
}

internal fun estimateFromPolarSink(
    netto: Float,
    verticalSpeed: Double,
    altitudeMeters: Double,
    qnhHpa: Double,
    sinkProvider: StillAirSinkProvider
): AirspeedEstimate? {
    val sinkEstimate = abs(netto.toDouble() - verticalSpeed)
    if (!sinkEstimate.isFinite() || sinkEstimate < MIN_SINK_FOR_IAS_MS) {
        return null
    }
    val tasMs = findSpeedForSink(sinkEstimate, sinkProvider) ?: return null
    val densityRatio = computeDensityRatio(altitudeMeters, qnhHpa)
    val indicatedMs = if (densityRatio > 0.0) tasMs * sqrt(densityRatio) else tasMs
    return AirspeedEstimate(indicatedMs = indicatedMs, trueMs = tasMs, source = AirspeedSource.POLAR_SINK)
}

internal fun findSpeedForSink(targetSinkMs: Double, sinkProvider: StillAirSinkProvider): Double? {
    var speed = IAS_SCAN_MIN_MS
    var bestSpeed: Double? = null
    var bestError = Double.POSITIVE_INFINITY
    while (speed <= IAS_SCAN_MAX_MS) {
        val sink = sinkProvider.sinkAtSpeed(speed) ?: break
        val error = abs(sink - targetSinkMs)
        if (error < bestError) {
            bestError = error
            bestSpeed = speed
        }
        speed += IAS_SCAN_STEP_MS
    }
    return bestSpeed
}

internal fun computeDensityRatio(altitudeMeters: Double, qnhHpa: Double): Double {
    val tempSeaLevelK = SEA_LEVEL_TEMP_CELSIUS + 273.15
    val theta = 1.0 + (TEMP_LAPSE_RATE_C_PER_M * altitudeMeters) / tempSeaLevelK
    if (theta <= 0.0) return 0.0
    val exponent = (-GRAVITY / (GAS_CONSTANT * TEMP_LAPSE_RATE_C_PER_M)) - 1.0
    return theta.pow(exponent)
}
