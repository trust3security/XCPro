package com.example.xcpro.xcprov1.model

import kotlin.math.sqrt

/**
 * Internal state maintained by the XCPro V1 filter.
 *
 * The state vector is organised as:
 * 0 -> barometric altitude (m)
 * 1 -> vertical speed of the glider (m/s)
 * 2 -> accelerometer bias estimate (m/s²)
 * 3 -> vertical air-mass velocity (m/s)
 * 4 -> horizontal wind X component (m/s, north-positive)
 * 5 -> horizontal wind Y component (m/s, east-positive)
 */
data class XcproV1State(
    val altitude: Double,
    val climbRate: Double,
    val accelBias: Double,
    val verticalWind: Double,
    val windX: Double,
    val windY: Double
) {
    fun windSpeed(): Double = sqrt(windX * windX + windY * windY)
}

/**
 * Snapshot delivered to the UI, audio engine and diagnostics subsystems.
 */
data class FlightDataV1Snapshot(
    val timestampMillis: Long,
    val actualClimb: Double,
    val potentialClimb: Double,
    val netto: Double,
    val verticalWind: Double,
    val windX: Double,
    val windY: Double,
    val confidence: Double,
    val climbTrend: Double,
    val sourceLabel: String,
    val diagnostics: DiagnosticsSnapshot
)

/**
 * Diagnostics exposed for logging and on-screen inspection.
 */
data class DiagnosticsSnapshot(
    val covarianceTrace: Double,
    val baroInnovation: Double,
    val accelInnovation: Double,
    val gpsInnovation: Double,
    val residualRms: Double
)
