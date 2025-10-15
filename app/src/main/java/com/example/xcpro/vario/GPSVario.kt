package com.example.xcpro.vario

/**
 * GPS-Based Vario (Long-term reference)
 *
 * Algorithm: GPS altitude differentiation with smoothing
 * - Uses GPS altitude changes over 5-10 seconds
 * - Low-pass filtered for smoothing
 * - Slower response but no drift
 *
 * Purpose:
 * - Long-term reference for drift validation
 * - Shows GPS vertical speed accuracy
 * - Useful for validating barometer drift correction
 *
 * Expected behavior:
 * - Slow response (5-10 second lag)
 * - Accurate over long term
 * - Poor for thermal detection (too slow)
 * - Good for cruise/final glide averages
 *
 * GPS Vertical Speed Characteristics:
 * - Accuracy: ±0.5 m/s typical
 * - Update rate: 1-10 Hz
 * - Lag: 5-10 seconds (needs averaging)
 * - Better with more satellites (8+)
 */
class GPSVario : IVarioCalculator {

    override val name = "GPS Vario"
    override val description = "GPS altitude differentiation (slow but accurate)"

    private val altitudeHistory = mutableListOf<AltitudeSample>()
    private var currentVerticalSpeed = 0.0

    private data class AltitudeSample(
        val altitude: Double,
        val timestamp: Long
    )

    companion object {
        private const val HISTORY_DURATION_MS = 10000L  // 10 seconds
        private const val MIN_SAMPLES = 3
    }

    override fun update(
        baroAltitude: Double,
        verticalAccel: Double,
        deltaTime: Double,
        gpsSpeed: Double,
        gpsAltitude: Double
    ): Double {
        val currentTime = System.currentTimeMillis()

        // Add current GPS altitude to history
        if (gpsAltitude > 0.0) {
            altitudeHistory.add(AltitudeSample(gpsAltitude, currentTime))
        }

        // Remove old samples (keep last 10 seconds)
        altitudeHistory.removeAll { currentTime - it.timestamp > HISTORY_DURATION_MS }

        // Need at least 3 samples to calculate vertical speed
        if (altitudeHistory.size < MIN_SAMPLES) {
            return 0.0
        }

        // Calculate vertical speed using linear regression over recent samples
        currentVerticalSpeed = calculateVerticalSpeedFromHistory()

        return currentVerticalSpeed
    }

    private fun calculateVerticalSpeedFromHistory(): Double {
        if (altitudeHistory.size < MIN_SAMPLES) {
            return 0.0
        }

        // Use last 5 seconds of data for calculation
        val recentSamples = altitudeHistory.takeLast(kotlin.math.min(altitudeHistory.size, 10))

        if (recentSamples.size < 2) {
            return 0.0
        }

        // Convert timestamps to seconds relative to first sample
        val firstTimestamp = recentSamples.first().timestamp
        val times = recentSamples.map { (it.timestamp - firstTimestamp) / 1000.0 }
        val altitudes = recentSamples.map { it.altitude }

        // Simple linear regression: altitude = slope * time + intercept
        // Slope = vertical speed (m/s)
        val slope = linearRegression(times, altitudes)

        // Apply deadband
        return if (kotlin.math.abs(slope) < 0.1) {
            0.0
        } else {
            slope
        }
    }

    private fun linearRegression(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.isEmpty()) {
            return 0.0
        }

        val n = x.size
        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).map { it.first * it.second }.sum()
        val sumX2 = x.map { it * it }.sum()

        val denominator = (n * sumX2 - sumX * sumX)
        if (kotlin.math.abs(denominator) < 0.001) {
            return 0.0
        }

        val slope = (n * sumXY - sumX * sumY) / denominator
        return slope
    }

    override fun reset() {
        altitudeHistory.clear()
        currentVerticalSpeed = 0.0
    }

    override fun getVerticalSpeed(): Double {
        return currentVerticalSpeed
    }

    override fun getDiagnostics(): String {
        return "$name: ${String.format("%.2f", currentVerticalSpeed)} m/s | " +
               "Samples: ${altitudeHistory.size} (${if (altitudeHistory.size >= MIN_SAMPLES) "OK" else "NEED MORE"})"
    }
}
