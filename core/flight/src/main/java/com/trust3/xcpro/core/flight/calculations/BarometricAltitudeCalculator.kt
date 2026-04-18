package com.trust3.xcpro.core.flight.calculations

import com.trust3.xcpro.core.common.logging.AppLogger
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
    HIGH,
    MEDIUM,
    LOW
}

class BarometricAltitudeCalculator {
    companion object {
        private const val TAG = "BaroCalc"
    }

    private val standardPressure = 1013.25
    private val temperatureSeaLevel = 288.15
    private val lapseRate = 0.0065
    private val gasConstant = 287.04
    private val gravity = 9.80665

    private var qnh = standardPressure
    private var isQnhCalibrated = false
    private var lastCalibrationTime = 0L

    fun calculateBarometricAltitude(
        rawPressureHPa: Double,
        gpsAltitudeMeters: Double? = null,
        gpsAccuracy: Double? = null,
        isGPSFixed: Boolean = false
    ): BarometricAltitudeData {
        val compensatedPressure = rawPressureHPa
        val referencePressure = if (isQnhCalibrated) qnh else standardPressure
        val baroAltitude = calculateIcaoBaroAltitude(compensatedPressure, referencePressure)
        val pressureAltitudeStd = calculateIcaoBaroAltitude(compensatedPressure, standardPressure)
        val confidence = determineConfidenceLevel(isGPSFixed, isQnhCalibrated, gpsAccuracy)
        val finalAltitude = baroAltitude
        val gpsDelta = if (gpsAltitudeMeters != null && !gpsAltitudeMeters.isNaN()) {
            finalAltitude - gpsAltitudeMeters
        } else {
            null
        }
        return BarometricAltitudeData(
            altitudeMeters = finalAltitude,
            qnh = qnh,
            isCalibrated = isQnhCalibrated,
            pressureHPa = compensatedPressure,
            temperatureCompensated = false,
            confidenceLevel = confidence,
            pressureAltitudeMeters = pressureAltitudeStd,
            gpsDeltaMeters = gpsDelta,
            lastCalibrationTime = lastCalibrationTime
        )
    }

    private fun calculateIcaoBaroAltitude(pressure: Double, seaLevelPressure: Double): Double {
        val pressureRatio = pressure / seaLevelPressure
        val exponent = (gasConstant * lapseRate) / gravity
        return (temperatureSeaLevel / lapseRate) * (1.0 - pressureRatio.pow(exponent))
    }

    private fun determineConfidenceLevel(
        isGPSFixed: Boolean,
        isCalibrated: Boolean,
        gpsAccuracy: Double?
    ): ConfidenceLevel = when {
        isCalibrated && isGPSFixed && (gpsAccuracy ?: 999.0) < 5.0 -> ConfidenceLevel.HIGH
        isCalibrated || isGPSFixed -> ConfidenceLevel.MEDIUM
        else -> ConfidenceLevel.LOW
    }

    fun setQNH(qnhHPa: Double, calibratedAtMillis: Long) {
        qnh = qnhHPa
        isQnhCalibrated = true
        lastCalibrationTime = calibratedAtMillis.coerceAtLeast(0L)
        AppLogger.i(TAG, "Manual QNH set to: ${qnhHPa.roundToInt()} hPa (by pilot)")
    }

    fun resetToStandardAtmosphere() {
        qnh = standardPressure
        isQnhCalibrated = false
        lastCalibrationTime = 0L
        AppLogger.i(TAG, "Reset to standard atmosphere")
    }
}
