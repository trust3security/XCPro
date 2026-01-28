package com.example.dfcards.calculations

import android.util.Log
import kotlin.math.pow
import kotlin.math.roundToInt

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

class BarometricAltitudeCalculator {

    companion object {
        private const val TAG = "BaroCalc"
    }

    // Aviation constants
    private val STANDARD_PRESSURE = 1013.25 // hPa
    private val TEMPERATURE_SEA_LEVEL = 288.15 // K
    private val LAPSE_RATE = 0.0065 // K/m
    private val GAS_CONSTANT = 287.04 // J/(kg*K)
    private val GRAVITY = 9.80665 // m/s^2

    // State variables
    private var qnh = STANDARD_PRESSURE
    private var isQNHCalibrated = false
    private var lastCalibrationTime = 0L

    /**
     * Calculate barometric altitude using aviation industry standards
     */
    fun calculateBarometricAltitude(
        rawPressureHPa: Double,
        gpsAltitudeMeters: Double? = null,
        gpsAccuracy: Double? = null,
        isGPSFixed: Boolean = false
    ): BarometricAltitudeData {

        // Temperature compensation removed: use raw pressure directly
        val compensatedPressure = rawPressureHPa

        // Use calibrated QNH or fall back to standard atmosphere
        val referencePressure = if (isQNHCalibrated) qnh else STANDARD_PRESSURE

        // Calculate altitude using ICAO Standard Atmosphere formula
        val baroAltitude = calculateICAOBaroAltitude(compensatedPressure, referencePressure)
        val pressureAltitudeStd = calculateICAOBaroAltitude(compensatedPressure, STANDARD_PRESSURE)

        // Keep altitude purely barometric once QNH is established; GPS altitude is too noisy (and
        // can toggle validity), which creates artificial step changes that then look like fake
        // vario spikes.
        val finalAltitude = baroAltitude

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
     * This is the proper way to update QNH during flight
     */
    fun setQNH(qnhHPa: Double, calibratedAtMillis: Long) {
        this.qnh = qnhHPa
        this.isQNHCalibrated = true
        this.lastCalibrationTime = calibratedAtMillis.coerceAtLeast(0L)
        Log.i(TAG, "Manual QNH set to: ${qnhHPa.roundToInt()} hPa (by pilot)")
    }

    /**
     * Reset to standard atmosphere
     */
    fun resetToStandardAtmosphere() {
        this.qnh = STANDARD_PRESSURE
        this.isQNHCalibrated = false
        this.lastCalibrationTime = 0L
        Log.i(TAG, "Reset to standard atmosphere")
    }
}
