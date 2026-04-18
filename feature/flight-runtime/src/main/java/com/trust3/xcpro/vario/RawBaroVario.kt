package com.trust3.xcpro.vario

/**
 * Raw Barometer Vario (No filtering - baseline for comparison)
 *
 * Algorithm: Simple altitude differentiation
 * - Vertical Speed = (Current Altitude - Previous Altitude) / Delta Time
 * - No Kalman filtering
 * - No smoothing
 *
 * Purpose:
 * - Show raw sensor noise
 * - Demonstrate why filtering is necessary
 * - Fastest possible response (zero lag) but very noisy
 *
 * Expected behavior:
 * - Very noisy (+/-0.5-2.0 m/s random fluctuations)
 * - Instant response (no filter lag)
 * - Unusable for actual flying (too jumpy)
 * - Good for understanding sensor characteristics
 */
class RawBaroVario : IVarioCalculator {

    override val name = "Raw Baro"
    override val description = "Simple differentiation (no filtering)"

    private var lastAltitude = 0.0
    private var currentVerticalSpeed = 0.0
    private var isInitialized = false

    override fun update(
        baroAltitude: Double,
        verticalAccel: Double,
        deltaTime: Double,
        gpsSpeed: Double,
        gpsAltitude: Double
    ): Double {
        if (!isInitialized) {
            lastAltitude = baroAltitude
            isInitialized = true
            currentVerticalSpeed = 0.0
            return 0.0
        }

        // Prevent division by zero
        if (deltaTime <= 0.001) {
            return currentVerticalSpeed
        }

        // Simple differentiation: V = Altitude / Time
        val deltaAltitude = baroAltitude - lastAltitude
        currentVerticalSpeed = deltaAltitude / deltaTime

        // Update last altitude
        lastAltitude = baroAltitude

        // Apply deadband to reduce noise around zero
        if (kotlin.math.abs(currentVerticalSpeed) < 0.05) {
            currentVerticalSpeed = 0.0
        }

        return currentVerticalSpeed
    }

    override fun reset() {
        lastAltitude = 0.0
        currentVerticalSpeed = 0.0
        isInitialized = false
    }

    override fun getVerticalSpeed(): Double {
        return currentVerticalSpeed
    }

    override fun getDiagnostics(): String {
        return "$name: ${String.format("%.2f", currentVerticalSpeed)} m/s | NO FILTER (expect noise!)"
    }
}
