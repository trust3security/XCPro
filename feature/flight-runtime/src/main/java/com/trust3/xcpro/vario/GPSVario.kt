package com.trust3.xcpro.vario

import kotlin.math.abs

/**
 * GPS-Based Vario (long-term reference)
 *
 * Computes a slow GPS vertical speed by running a linear regression over a time window.
 * Intended as a drift reference; baro remains the primary vario source.
 */
class GPSVario : IVarioCalculator {

    override val name = "GPS Vario"
    override val description = "GPS altitude regression (slow, drift-free reference)"

    private val altitudeHistory = mutableListOf<AltitudeSample>()
    private var currentVerticalSpeed = 0.0

    private data class AltitudeSample(
        val altitudeMeters: Double,
        val timestampMillis: Long
    )

    override fun update(
        baroAltitude: Double,
        verticalAccel: Double,
        deltaTime: Double,
        gpsSpeed: Double,
        gpsAltitude: Double
    ): Double {
        // No-op: GPS vario updates are driven by updateFromGpsFix with real fix timestamps.
        return currentVerticalSpeed
    }

    fun updateFromGpsFix(gpsAltitudeMeters: Double, gpsTimestampMillis: Long): Double {
        if (!gpsAltitudeMeters.isFinite() || gpsTimestampMillis <= 0L) return currentVerticalSpeed

        val lastTimestamp = altitudeHistory.lastOrNull()?.timestampMillis
        if (lastTimestamp != null) {
            val dtMillis = gpsTimestampMillis - lastTimestamp
            if (dtMillis <= 0L || dtMillis < MIN_SAMPLE_INTERVAL_MS) return currentVerticalSpeed
        }

        altitudeHistory.add(AltitudeSample(gpsAltitudeMeters, gpsTimestampMillis))
        altitudeHistory.removeAll { gpsTimestampMillis - it.timestampMillis > HISTORY_DURATION_MS }

        currentVerticalSpeed = calculateVerticalSpeed()
        return currentVerticalSpeed
    }

    private fun calculateVerticalSpeed(): Double {
        if (altitudeHistory.size < MIN_SAMPLES) return 0.0

        val spanMillis = altitudeHistory.last().timestampMillis - altitudeHistory.first().timestampMillis
        if (spanMillis < MIN_SPAN_MS) return 0.0

        val t0 = altitudeHistory.first().timestampMillis

        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2 = 0.0
        val n = altitudeHistory.size

        for (sample in altitudeHistory) {
            val xSeconds = (sample.timestampMillis - t0) / 1000.0
            val y = sample.altitudeMeters
            sumX += xSeconds
            sumY += y
            sumXY += xSeconds * y
            sumX2 += xSeconds * xSeconds
        }

        val denominator = n * sumX2 - sumX * sumX
        if (abs(denominator) < 1e-9) return 0.0

        val slope = (n * sumXY - sumX * sumY) / denominator
        if (!slope.isFinite()) return 0.0

        val deBanded = if (abs(slope) < DEAD_BAND_MS) 0.0 else slope
        return deBanded.coerceIn(-MAX_ABS_OUTPUT_MS, MAX_ABS_OUTPUT_MS)
    }

    override fun reset() {
        altitudeHistory.clear()
        currentVerticalSpeed = 0.0
    }

    override fun getVerticalSpeed(): Double = currentVerticalSpeed

    override fun getDiagnostics(): String {
        val spanSeconds = if (altitudeHistory.size >= 2) {
            (altitudeHistory.last().timestampMillis - altitudeHistory.first().timestampMillis) / 1000.0
        } else 0.0

        return "$name: ${String.format("%.2f", currentVerticalSpeed)} m/s | " +
            "samples=${altitudeHistory.size} span=${String.format("%.1f", spanSeconds)}s"
    }

    companion object {
        private const val HISTORY_DURATION_MS = 10_000L
        private const val MIN_SAMPLES = 3
        private const val MIN_SPAN_MS = 3_000L
        private const val MIN_SAMPLE_INTERVAL_MS = 1_000L
        private const val DEAD_BAND_MS = 0.1
        private const val MAX_ABS_OUTPUT_MS = 20.0
    }
}
