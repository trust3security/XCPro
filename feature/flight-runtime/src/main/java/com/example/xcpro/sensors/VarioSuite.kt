package com.example.xcpro.sensors

import com.example.xcpro.core.flight.filters.VarioFilterDiagnostics
import com.example.xcpro.vario.ComplementaryVario
import com.example.xcpro.vario.GPSVario
import com.example.xcpro.vario.LegacyKalmanVario
import com.example.xcpro.vario.OptimizedKalmanVario
import com.example.xcpro.vario.RawBaroVario

/**
 * Encapsulates the fleet of vario implementations so FlightDataCalculator
 * stays under the 500 LOC guardrail and mirrors legacy glide computer setup.
 */
internal class VarioSuite {
    private val lock = Any()
    private val optimized = OptimizedKalmanVario()     // Priority 1: R=0.5m
    private val legacy = LegacyKalmanVario()           // Baseline: R=2.0m
    private val raw = RawBaroVario()                   // No filtering
    private val gps = GPSVario()                       // GPS-based
    private val complementary = ComplementaryVario()   // Future (Priority 3)

    fun updateAll(
        baroAltitude: Double,
        verticalAccel: Double,
        deltaTime: Double,
        gpsSpeed: Double,
        gpsAltitude: Double
    ) {
        synchronized(lock) {
            optimized.update(baroAltitude, verticalAccel, deltaTime, gpsSpeed, gpsAltitude)
            legacy.update(baroAltitude, verticalAccel, deltaTime, gpsSpeed, gpsAltitude)
            raw.update(baroAltitude, 0.0, deltaTime, gpsSpeed, gpsAltitude)
            complementary.update(baroAltitude, verticalAccel, deltaTime, gpsSpeed, gpsAltitude)
        }
    }

    fun updateGpsVario(gpsAltitudeMeters: Double, gpsTimestampMillis: Long): Double =
        synchronized(lock) {
            gps.updateFromGpsFix(gpsAltitudeMeters = gpsAltitudeMeters, gpsTimestampMillis = gpsTimestampMillis)
        }

    fun resetAll() {
        synchronized(lock) {
            optimized.reset()
            legacy.reset()
            raw.reset()
            gps.reset()
            complementary.reset()
        }
    }

    fun verticalSpeeds(): Map<String, Double> = synchronized(lock) {
        mapOf(
            "optimized" to optimized.getVerticalSpeed(),
            "legacy" to legacy.getVerticalSpeed(),
            "raw" to raw.getVerticalSpeed(),
            "gps" to gps.getVerticalSpeed(),
            "complementary" to complementary.getVerticalSpeed()
        )
    }

    fun gpsVerticalSpeed(): Double = synchronized(lock) { gps.getVerticalSpeed() }

    fun optimizedDiagnostics(
        gpsAccuracy: Double,
        gpsSatelliteCount: Int
    ): VarioFilterDiagnostics = synchronized(lock) {
        optimized.getDiagnosticsData(
            gpsAccuracy = gpsAccuracy,
            gpsSatelliteCount = gpsSatelliteCount
        )
    }
}
