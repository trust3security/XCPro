package com.trust3.xcpro.sensors.domain

import kotlin.math.pow

fun pressureToAltitudeMeters(pressureHpa: Double): Double {
    if (!pressureHpa.isFinite() || pressureHpa <= 0.0) return Double.NaN
    return PRESSURE_ALTITUDE_BASE_METERS *
        (1.0 - (pressureHpa / FlightMetricsConstants.SEA_LEVEL_PRESSURE_HPA).pow(PRESSURE_ALTITUDE_EXPONENT))
}

fun computeDensityRatio(altitudeMeters: Double, qnhHpa: Double): Double {
    val tempSeaLevelK = FlightMetricsConstants.SEA_LEVEL_TEMP_CELSIUS + 273.15
    val theta = 1.0 + (FlightMetricsConstants.TEMP_LAPSE_RATE_C_PER_M * altitudeMeters) / tempSeaLevelK
    if (theta <= 0.0) return 0.0
    val exponent = (-FlightMetricsConstants.GRAVITY /
        (FlightMetricsConstants.GAS_CONSTANT * FlightMetricsConstants.TEMP_LAPSE_RATE_C_PER_M)) - 1.0
    val standardDensityRatio = theta.pow(exponent)
    val qnhRatio = (qnhHpa / FlightMetricsConstants.SEA_LEVEL_PRESSURE_HPA)
        .takeIf { it.isFinite() && it > 0.0 }
        ?: 1.0
    return standardDensityRatio * qnhRatio
}

private const val PRESSURE_ALTITUDE_BASE_METERS = 44330.0
private const val PRESSURE_ALTITUDE_EXPONENT = 0.1903
