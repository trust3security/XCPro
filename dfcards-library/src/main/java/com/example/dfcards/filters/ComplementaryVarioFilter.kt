package com.example.dfcards.filters

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Complementary Vario Filter - Zero-lag alternative to Kalman filter
 *
 * PRIORITY 3: VARIO_IMPROVEMENTS.md
 *
 * Concept: Complementary frequency domain filtering
 * - Barometer: Low-pass filter (slow but accurate, no drift)
 * - Accelerometer: High-pass filter (fast but drifts)
 * - Result: Fast response + no drift
 *
 * Trade-offs vs Kalman:
 * + 10-100x faster computation (<1ms vs 10-50ms)
 * + Zero computational lag
 * + Simpler to tune
 * - Less optimal in noisy conditions
 * - Fixed filter coefficients (not adaptive)
 *
 * Use cases:
 * - Instant thermal entry detection
 * - Audio feedback mode (pilot wants immediate beep)
 * - Low-power mode (less CPU usage)
 *
 * Performance target: <50ms thermal detection lag
 */
class ComplementaryVarioFilter(
    private val config: AdaptiveVarioConfig = AdaptiveVarioConfig()
) {

    // State variables
    private var baroVerticalSpeed = 0.0       // m/s from barometer differentiation
    private var accelVerticalSpeed = 0.0      // m/s from accelerometer integration
    private var lastBaroAltitude = 0.0        // m
    private var lastUpdateTime = 0L           // ms

    // High-pass filter state for accelerometer (remove DC bias)
    private var accelBias = 0.0               // m/s²
    private val BIAS_TIME_CONSTANT = 5.0      // seconds

    // Smoothing for barometer vertical speed
    private var lastBaroVSpeed = 0.0
    private val baroVarianceTracker = AdaptiveVarianceTracker(config.baroVarianceWindowSize)
    private var lastFusedVerticalSpeed = 0.0

    /**
     * Update filter with new sensor readings
     *
     * @param baroAltitude Current barometric altitude (m)
     * @param verticalAccel Vertical acceleration from IMU (m/s²)
     * @param deltaTime Time since last update (s)
     * @return Fused vertical speed (m/s)
     */
    fun update(
        baroAltitude: Double,
        verticalAccel: Double,
        deltaTime: Double
    ): ComplementaryVarioResult {

        val currentTime = System.currentTimeMillis()

        // Initialize on first call
        if (lastUpdateTime == 0L) {
            lastBaroAltitude = baroAltitude
            lastUpdateTime = currentTime
            accelBias = verticalAccel
            lastBaroVSpeed = 0.0
            return ComplementaryVarioResult(0.0, 0.0, 0.0)
        }

        // Prevent invalid delta times
        if (deltaTime <= 0.001 || deltaTime > 1.0) {
            return ComplementaryVarioResult(
                lastFusedVerticalSpeed,
                baroVerticalSpeed,
                accelVerticalSpeed
            )
        }

        // ═══════════════════════════════════════════════════
        // BAROMETER PATH (Low-pass filter)
        // ═══════════════════════════════════════════════════

        // Differentiate barometer altitude to get vertical speed
        val rawBaroVSpeed = (baroAltitude - lastBaroAltitude) / deltaTime
        lastBaroAltitude = baroAltitude

        // Apply simple low-pass filter to barometer vertical speed
        // (removes high-frequency noise from pressure fluctuations)
        val baroLPF = 0.7  // Low-pass coefficient
        baroVerticalSpeed = baroLPF * rawBaroVSpeed + (1.0 - baroLPF) * lastBaroVSpeed
        lastBaroVSpeed = baroVerticalSpeed
        baroVarianceTracker.add(baroVerticalSpeed)

        // ═══════════════════════════════════════════════════
        // ACCELEROMETER PATH (High-pass filter)
        // ═══════════════════════════════════════════════════

        // Update accelerometer bias estimate (slow drift correction)
        val biasAlpha = deltaTime / BIAS_TIME_CONSTANT
        accelBias += biasAlpha * (verticalAccel - accelBias)

        // Remove bias from acceleration
        val accelCorrected = verticalAccel - accelBias

        // Integrate acceleration to get velocity change
        val accelDeltaV = accelCorrected * deltaTime

        // Update accelerometer vertical speed with high-pass filter
        // (allows fast changes, but removes long-term drift)
        val accelHPF = 0.98  // High-pass coefficient (close to 1.0 = more drift allowed)
        accelVerticalSpeed = accelHPF * (accelVerticalSpeed + accelDeltaV)

        // ═══════════════════════════════════════════════════
        // COMPLEMENTARY FUSION
        // ═══════════════════════════════════════════════════

        val sigma2Baro = baroVarianceTracker.variance()
        val tauEff = (config.tauBaseSeconds / (1.0 + sqrt(sigma2Baro)))
            .coerceIn(config.tauMinSeconds, config.tauMaxSeconds)
        val accelWeight = (deltaTime / (tauEff + deltaTime)).coerceIn(0.0, 1.0)
        val baroWeight = 1.0 - accelWeight

        // Fuse barometer (accurate, slow) + accelerometer (fast, drifts)
        val fusedVerticalSpeed = baroWeight * baroVerticalSpeed + accelWeight * accelVerticalSpeed

        // Apply deadband (eliminate noise around zero)
        val deadband = 0.02  // m/s (4 fpm - same as Kalman filter)
        val finalVerticalSpeed = if (abs(fusedVerticalSpeed) < deadband) 0.0 else fusedVerticalSpeed

        lastUpdateTime = currentTime
        lastFusedVerticalSpeed = finalVerticalSpeed

        return ComplementaryVarioResult(
            verticalSpeed = finalVerticalSpeed,
            baroComponent = baroVerticalSpeed,
            accelComponent = accelVerticalSpeed
        )
    }

    /**
     * Reset filter state
     */
    fun reset() {
        baroVerticalSpeed = 0.0
        accelVerticalSpeed = 0.0
        lastBaroAltitude = 0.0
        lastUpdateTime = 0L
        accelBias = 0.0
        lastBaroVSpeed = 0.0
        lastFusedVerticalSpeed = 0.0
        baroVarianceTracker.reset()
    }

    /**
     * Get current vertical speed without updating
     */
    fun getVerticalSpeed(): Double {
        return lastFusedVerticalSpeed
    }

    /**
     * Get diagnostic info
     */
    fun getDiagnostics(): String {
        val vSpeed = lastFusedVerticalSpeed
        return "Comp: V/S=${String.format("%.2f", vSpeed)}m/s, " +
               "Baro=${String.format("%.2f", baroVerticalSpeed)}m/s, " +
               "Accel=${String.format("%.2f", accelVerticalSpeed)}m/s, " +
               "Bias=${String.format("%.3f", accelBias)}m/s²"
    }
}

/**
 * Complementary filter result
 */
data class ComplementaryVarioResult(
    val verticalSpeed: Double,     // m/s (fused result)
    val baroComponent: Double,      // m/s (barometer contribution)
    val accelComponent: Double      // m/s (accelerometer contribution)
)
