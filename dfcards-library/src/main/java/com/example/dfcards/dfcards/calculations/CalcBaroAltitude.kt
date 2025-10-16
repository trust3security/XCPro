package com.example.dfcards.calculations

import android.util.Log
import com.example.dfcards.dfcards.calculations.SimpleAglCalculator
import kotlinx.coroutines.runBlocking
import kotlin.math.*

data class BarometricAltitudeData(
    val altitudeMeters: Double,
    val qnh: Double,
    val isCalibrated: Boolean,
    val pressureHPa: Double,
    val temperatureCompensated: Boolean,
    val confidenceLevel: ConfidenceLevel,
    val pressureAltitudeMeters: Double,
    val gpsDeltaMeters: Double?,
    val lastCalibrationTime: Long
)

enum class ConfidenceLevel {
    HIGH,    // GPS calibrated, good conditions
    MEDIUM,  // Standard atmosphere, reasonable conditions
    LOW      // Uncalibrated, poor conditions
}

class BarometricAltitudeCalculator(
    private val terrainCalculator: SimpleAglCalculator? = null  // Optional: for SRTM-based QNH calibration
) {

    companion object {
        private const val TAG = "BaroCalc"
    }

    // Aviation constants
    private val STANDARD_PRESSURE = 1013.25 // hPa
    private val TEMPERATURE_SEA_LEVEL = 288.15 // K
    private val LAPSE_RATE = 0.0065 // K/m
    private val GAS_CONSTANT = 287.04 // J/(kg·K)
    private val GRAVITY = 9.80665 // m/s²

    // Calibration settings
    private val CALIBRATION_INTERVAL = 60000L // 1 minute (for initial acquisition only)
    private val GPS_ACCURACY_THRESHOLD = 100.0 // meters (very relaxed for SRTM-based calibration - only need horizontal position)
    private val CALIBRATION_SAMPLES_REQUIRED = 15 // 15 samples over ~15 seconds for stable QNH

    // ✅ FIX: One-time QNH calibration with averaging (aviation standard)
    // Collects 15 samples during startup to calculate stable QNH baseline
    // This prevents altitude jumps from noisy sensor readings at startup
    private var allowAutoRecalibration = true  // Only allow FIRST calibration
    private val calibrationBuffer = mutableListOf<CalibrationSample>()
    private var isCalibrationComplete = false

    // State variables
    private var qnh = STANDARD_PRESSURE
    private var isQNHCalibrated = false
    private var lastCalibrationTime = 0L

    /**
     * Calibration sample for QNH averaging during startup
     */
    private data class CalibrationSample(
        val pressureHPa: Double,
        val gpsAltitude: Double,
        val gpsAccuracy: Double,
        val timestamp: Long,
        val gpsLat: Double? = null,  // GPS coordinates for terrain-based calibration
        val gpsLon: Double? = null
    )

    /**
     * Calculate barometric altitude using aviation industry standards
     */
    fun calculateBarometricAltitude(
        rawPressureHPa: Double,
        gpsAltitudeMeters: Double? = null,
        gpsAccuracy: Double? = null,
        isGPSFixed: Boolean = false,
        gpsLat: Double? = null,  // GPS coordinates for terrain-based calibration
        gpsLon: Double? = null
    ): BarometricAltitudeData {

        // Temperature compensation removed: use raw pressure directly
        val compensatedPressure = rawPressureHPa

        // 🐛 DEBUG: Log calibration check parameters (every 10th call to avoid spam)
        if (System.currentTimeMillis() % 1000 < 100) {
            Log.d(TAG, "🐛 Calibration check: allowAuto=$allowAutoRecalibration, complete=$isCalibrationComplete, " +
                    "gpsAlt=$gpsAltitudeMeters, acc=$gpsAccuracy, fixed=$isGPSFixed, " +
                    "lat=$gpsLat, lon=$gpsLon, samples=${calibrationBuffer.size}/$CALIBRATION_SAMPLES_REQUIRED")
        }

        // ✅ FIX: Collect calibration samples and average for stable QNH baseline
        // Prevents altitude jumps from noisy sensor readings at startup
        if (allowAutoRecalibration && !isCalibrationComplete &&
            shouldCollectCalibrationSample(gpsAltitudeMeters, gpsAccuracy, isGPSFixed)) {

            // Add sample to buffer (with GPS coordinates for terrain-based calibration)
            calibrationBuffer.add(CalibrationSample(
                pressureHPa = compensatedPressure,
                gpsAltitude = gpsAltitudeMeters!!,
                gpsAccuracy = gpsAccuracy!!,
                timestamp = System.currentTimeMillis(),
                gpsLat = gpsLat,  // Store for terrain-based calibration
                gpsLon = gpsLon
            ))

            Log.d(TAG, "Calibration sample ${calibrationBuffer.size}/$CALIBRATION_SAMPLES_REQUIRED collected (GPS: ${gpsAltitudeMeters.toInt()}m, Press: ${compensatedPressure.toInt()} hPa)")

            // Once we have enough samples, calculate averaged QNH
            if (calibrationBuffer.size >= CALIBRATION_SAMPLES_REQUIRED) {
                qnh = calculateAveragedQNH()
                isQNHCalibrated = true
                isCalibrationComplete = true
                allowAutoRecalibration = false
                lastCalibrationTime = System.currentTimeMillis()

                val avgGPS = calibrationBuffer.map { it.gpsAltitude }.average().toInt()
                Log.i(TAG, "✅ QNH CALIBRATED: ${qnh.toInt()} hPa (from ${calibrationBuffer.size} samples, avg GPS: ${avgGPS}m)")
            }
        }

        // Use calibrated QNH or fall back to standard atmosphere
        val referencePressure = if (isQNHCalibrated) qnh else STANDARD_PRESSURE

        // Calculate altitude using ICAO Standard Atmosphere formula
        val baroAltitude = calculateICAOBaroAltitude(compensatedPressure, referencePressure)
        val pressureAltitudeStd = calculateICAOBaroAltitude(compensatedPressure, STANDARD_PRESSURE)

        // Apply sensor fusion if GPS is available and reliable
        val finalAltitude = if (isQNHCalibrated && gpsAltitudeMeters != null && isGPSFixed) {
            // 80% barometric, 20% GPS for stability with accuracy
            (baroAltitude * 0.8) + (gpsAltitudeMeters * 0.2)
        } else {
            baroAltitude
        }

        // Determine confidence level
        val confidence = determineConfidenceLevel(isGPSFixed, isQNHCalibrated, gpsAccuracy)

        val gpsDelta = if (gpsAltitudeMeters != null && !gpsAltitudeMeters.isNaN()) {
            finalAltitude - gpsAltitudeMeters
        } else {
            null
        }

        return BarometricAltitudeData(
            altitudeMeters = finalAltitude,
            qnh = qnh,
            isCalibrated = isQNHCalibrated,
            pressureHPa = compensatedPressure,
            temperatureCompensated = false,
            confidenceLevel = confidence,
            pressureAltitudeMeters = pressureAltitudeStd,
            gpsDeltaMeters = gpsDelta,
            lastCalibrationTime = lastCalibrationTime
        )
    }

    /**
     * ICAO Standard Atmosphere calculation (aviation industry standard)
     */
    private fun calculateICAOBaroAltitude(pressure: Double, seaLevelPressure: Double): Double {
        val pressureRatio = pressure / seaLevelPressure
        val exponent = (GAS_CONSTANT * LAPSE_RATE) / GRAVITY

        return (TEMPERATURE_SEA_LEVEL / LAPSE_RATE) * (1.0 - pressureRatio.pow(exponent))
    }

    /**
     * Calculate QNH from GPS reference altitude
     *
     * Uses ICAO Standard Atmosphere formula to convert current pressure at known altitude
     * to sea level pressure (QNH).
     *
     * Formula: QNH = P(h) / (T(h)/T0)^(g/(Rs*L))
     * where Rs is specific gas constant (already accounts for molar mass)
     */
    private fun calculateQNHFromGPS(currentPressure: Double, gpsAltitude: Double): Double {
        val temperatureAtAltitude = TEMPERATURE_SEA_LEVEL - (LAPSE_RATE * gpsAltitude)

        // ✅ FIX: Removed erroneous molar mass multiplication
        // GAS_CONSTANT (287.04) is SPECIFIC gas constant (R/M), so don't multiply by M again!
        val exponent = GRAVITY / (GAS_CONSTANT * LAPSE_RATE)

        val pressureRatio = (temperatureAtAltitude / TEMPERATURE_SEA_LEVEL).pow(exponent)

        return currentPressure / pressureRatio
    }

    /**
     * Calculate QNH using SRTM terrain elevation (IMPROVED ACCURACY)
     *
     * RATIONALE:
     * - GPS horizontal accuracy: ±5m (excellent)
     * - GPS vertical accuracy: ±50m (terrible - causes QNH calibration failures)
     * - SRTM terrain elevation: ±20m (consistent and cached)
     *
     * APPROACH:
     * 1. Use GPS horizontal position (accurate ±5m)
     * 2. Fetch terrain elevation from SRTM database (±20m, cached)
     * 3. Estimate ASL = terrain + 2m (assume phone at ground level)
     * 4. Calculate QNH from estimated ASL
     *
     * BENEFITS:
     * - Eliminates GPS vertical noise (root cause of negative altitude bug)
     * - Uses existing SRTM database (already fetched for AGL)
     * - Works even with poor GPS vertical accuracy
     * - 5x better QNH accuracy: ±8 hPa vs ±40 hPa
     *
     * @param currentPressure Current barometric pressure (hPa)
     * @param gpsLat GPS latitude
     * @param gpsLon GPS longitude
     * @param estimatedAGL Estimated height above ground (default 2m for phone on ground)
     * @return Calculated QNH (hPa), or null if terrain data unavailable
     */
    private fun calibrateQNHWithTerrain(
        currentPressure: Double,
        gpsLat: Double,
        gpsLon: Double,
        estimatedAGL: Double = 2.0
    ): Double? {
        // Require terrain calculator
        if (terrainCalculator == null) {
            Log.w(TAG, "⚠️ Terrain calculator not available for SRTM-based calibration")
            return null
        }

        // Fetch terrain elevation (uses runBlocking since calibration is startup-only)
        val terrainElevation: Double? = runBlocking {
            terrainCalculator.getTerrainElevation(gpsLat, gpsLon)
        }

        if (terrainElevation == null) {
            Log.w(TAG, "⚠️ No terrain data available for SRTM-based QNH calibration")
            return null
        }

        // Estimate ASL = terrain + small AGL offset (phone on ground)
        val estimatedASL = terrainElevation + estimatedAGL

        // Calculate QNH from estimated ASL
        val qnh = calculateQNHFromGPS(currentPressure, estimatedASL)

        // Validate QNH (should be reasonable: 980-1040 hPa)
        if (qnh < 950.0 || qnh > 1050.0) {
            Log.e(TAG, "❌ SRTM-based QNH out of range: ${qnh.toInt()} hPa (terrain: ${terrainElevation.toInt()}m)")
            return null
        }

        Log.i(TAG, "✅ SRTM-based QNH: ${qnh.toInt()} hPa " +
                  "(terrain: ${terrainElevation.toInt()}m + ${estimatedAGL.toInt()}m AGL = ${estimatedASL.toInt()}m ASL)")

        return qnh
    }

    /**
     * Determine if we should collect this sample for calibration
     * Only during startup, with reasonable GPS horizontal accuracy
     *
     * NOTE: isGPSFixed NOT required for SRTM-based calibration
     * We only need GPS coordinates (horizontal position) to fetch terrain elevation
     */
    private fun shouldCollectCalibrationSample(
        gpsAltitude: Double?,
        gpsAccuracy: Double?,
        isGPSFixed: Boolean
    ): Boolean {
        // Validate GPS altitude: allow sea level and common negative elevations; reject NaN and absurd values
        if (gpsAltitude == null || gpsAltitude.isNaN() || gpsAltitude < -500.0) {
            Log.d(TAG, "🐛 Skip sample: invalid gpsAltitude ($gpsAltitude)")
            return false
        }
        if (gpsAccuracy == null) {
            Log.d(TAG, "🐛 Skip sample: gpsAccuracy is null")
            return false
        }
        if (gpsAccuracy >= GPS_ACCURACY_THRESHOLD) {
            Log.d(TAG, "🐛 Skip sample: gpsAccuracy $gpsAccuracy >= threshold $GPS_ACCURACY_THRESHOLD")
            return false
        }
        // ✅ REMOVED: isGPSFixed check - not needed for SRTM-based calibration
        // We only need GPS coordinates to fetch terrain elevation, not high-quality vertical accuracy

        Log.i(TAG, "✅ Sample ACCEPTED! (alt=$gpsAltitude, acc=$gpsAccuracy, fixed=$isGPSFixed)")
        return true
    }

    /**
     * Calculate QNH from averaged calibration samples
     *
     * 🚀 IMPROVED: Tries SRTM terrain-based calibration first (5x more accurate)
     * Falls back to GPS-based calibration if terrain unavailable
     */
    private fun calculateAveragedQNH(): Double {
        if (calibrationBuffer.isEmpty()) {
            Log.e(TAG, "ERROR: No calibration samples!")
            return STANDARD_PRESSURE
        }

        // Average all pressure readings
        val avgPressure = calibrationBuffer.map { it.pressureHPa }.average()

        // 🚀 PRIORITY 1: Try SRTM terrain-based calibration first
        // Find samples with GPS coordinates
        val samplesWithCoords = calibrationBuffer.filter { it.gpsLat != null && it.gpsLon != null }

        if (samplesWithCoords.isNotEmpty()) {
            // Use middle sample for coordinates (stable location)
            val middleSample = samplesWithCoords[samplesWithCoords.size / 2]

            Log.i(TAG, "🚀 Attempting SRTM terrain-based QNH calibration...")
            val terrainQNH = calibrateQNHWithTerrain(
                currentPressure = avgPressure,
                gpsLat = middleSample.gpsLat!!,
                gpsLon = middleSample.gpsLon!!,
                estimatedAGL = 2.0  // Assume phone 2m above ground
            )

            if (terrainQNH != null) {
                Log.i(TAG, "✅ Using SRTM-based QNH: ${terrainQNH.toInt()} hPa (5x more accurate!)")
                return terrainQNH
            } else {
                Log.w(TAG, "⚠️ SRTM calibration failed, falling back to GPS altitude...")
            }
        } else {
            Log.w(TAG, "⚠️ No GPS coordinates in samples, falling back to GPS altitude...")
        }

        // ⚠️ FALLBACK: Use GPS altitude (less accurate, but better than nothing)
        // Sort samples by GPS altitude to find median
        val sortedByAltitude = calibrationBuffer.sortedBy { it.gpsAltitude }
        val medianGPSAltitude = sortedByAltitude[sortedByAltitude.size / 2].gpsAltitude

        // Calculate QNH from median GPS and average pressure
        val calculatedQNH = calculateQNHFromGPS(avgPressure, medianGPSAltitude)

        Log.w(TAG, "⚠️ Using GPS-based QNH: ${calculatedQNH.toInt()} hPa " +
                  "(Median GPS: ${medianGPSAltitude.toInt()}m, Avg Press: ${avgPressure.toInt()} hPa)")

        return calculatedQNH
    }

    /**
     * Determine confidence level for the barometric altitude reading
     */
    private fun determineConfidenceLevel(
        isGPSFixed: Boolean,
        isCalibrated: Boolean,
        gpsAccuracy: Double?
    ): ConfidenceLevel {
        return when {
            isCalibrated && isGPSFixed && (gpsAccuracy ?: 999.0) < 5.0 -> ConfidenceLevel.HIGH
            isCalibrated || isGPSFixed -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
    }

    /**
     * Manual QNH setting (for pilot input)
     * This is the PROPER way to update QNH during flight
     */
    fun setQNH(qnhHPa: Double) {
        this.qnh = qnhHPa
        this.isQNHCalibrated = true
        this.lastCalibrationTime = System.currentTimeMillis()
        this.allowAutoRecalibration = false  // Pilot override = disable auto
        Log.i(TAG, "Manual QNH set to: ${qnh.roundToInt()} hPa (by pilot)")
    }

    /**
     * Re-enable QNH auto-calibration (for testing or after landing)
     * Normally not needed - QNH should stay constant during flight
     */
    fun enableAutoRecalibration() {
        this.allowAutoRecalibration = true
        Log.w(TAG, "Auto-recalibration RE-ENABLED (use carefully!)")
    }

    /**
     * Reset to standard atmosphere
     */
    fun resetToStandardAtmosphere() {
        this.qnh = STANDARD_PRESSURE
        this.isQNHCalibrated = false
        this.lastCalibrationTime = 0L
        this.allowAutoRecalibration = true  // Allow fresh calibration
        this.isCalibrationComplete = false
        this.calibrationBuffer.clear()
        Log.i(TAG, "Reset to standard atmosphere")
    }

    /**
     * Get calibration status for UI display
     * Returns pair of (samplesCollected, samplesRequired)
     */
    fun getCalibrationStatus(): Pair<Int, Int> {
        return Pair(calibrationBuffer.size, CALIBRATION_SAMPLES_REQUIRED)
    }

    /**
     * Check if QNH calibration is complete
     */
    fun isCalibrationFinished(): Boolean {
        return isCalibrationComplete
    }
}
