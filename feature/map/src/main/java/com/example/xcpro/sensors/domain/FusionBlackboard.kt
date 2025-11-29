package com.example.xcpro.sensors.domain

import com.example.dfcards.calculations.BarometricAltitudeData
import com.example.xcpro.sensors.FixedSampleAverageWindow
import com.example.xcpro.sensors.TimedAverageWindow
import com.example.xcpro.sensors.addSamplesForElapsedSeconds
import com.example.xcpro.sensors.domain.AirspeedEstimate
import com.example.xcpro.sensors.domain.AirspeedSource
import kotlin.math.abs

/**
 * Blackboard/fusion helper that owns mutable sensor/fusion state (windows, deltas, QNH jump)
 * and exposes read-only aggregates to the use case.
 */
internal class FusionBlackboard {
    private val bruttoAverageWindow = FixedSampleAverageWindow(FlightMetricsConstants.AVERAGE_WINDOW_SECONDS)
    private val nettoAverageWindow = FixedSampleAverageWindow(FlightMetricsConstants.AVERAGE_WINDOW_SECONDS)
    private val nettoDisplayWindow = TimedAverageWindow(FlightMetricsConstants.NETTO_DISPLAY_WINDOW_MS)

    private var lastBruttoSampleTime = 0L
    private var lastNettoSampleTime = 0L
    private var lastThermalState = false

    private var lastNettoValue = Double.NaN

    private var lastQnh: Double? = null
    private var lastCalibrationTime: Long = 0L

    // Airspeed hold
    private var lastIndicatedMs = Double.NaN
    private var lastTrueMs = Double.NaN
    private var lastAirspeedSource = AirspeedSource.GPS_GROUND
    private var lastAirspeedTimestamp = 0L

    data class AverageOutputs(
        val bruttoAverage30s: Double,
        val bruttoAverage30sValid: Boolean,
        val nettoAverage30s: Double,
        val displayNettoRaw: Double
    )

    fun detectCalibrationChange(qnh: Double, baroResult: BarometricAltitudeData?, currentTime: Long): Boolean {
        val qnhJump = lastQnh?.let { abs(it - qnh) > FlightMetricsConstants.QNH_JUMP_THRESHOLD_HPA } ?: false
        val calibrationChanged = qnhJump ||
            (baroResult?.lastCalibrationTime?.let { it > 0 && it != lastCalibrationTime } ?: false)
        if (calibrationChanged) {
            lastCalibrationTime = baroResult?.lastCalibrationTime?.takeIf { it > 0 } ?: currentTime
        }
        lastQnh = qnh
        return calibrationChanged
    }

    fun resolveNettoSampleValue(rawNetto: Double, nettoValid: Boolean): Double {
        if (nettoValid) return rawNetto
        val fallback = lastNettoValue
        return if (!fallback.isNaN()) fallback else rawNetto
    }

    fun resolveAirspeedHold(
        airspeedEstimate: AirspeedEstimate?,
        now: Long
    ): AirspeedEstimate? {
        airspeedEstimate?.let {
            rememberAirspeed(it, now)
            return it
        }
        val withinHold = now - lastAirspeedTimestamp <= FlightMetricsConstants.SPEED_HOLD_MS
        return if (withinHold && lastIndicatedMs.isFinite() && lastTrueMs.isFinite()) {
            AirspeedEstimate(lastIndicatedMs, lastTrueMs, lastAirspeedSource)
        } else null
    }

    private fun rememberAirspeed(estimate: AirspeedEstimate, now: Long) {
        lastIndicatedMs = estimate.indicatedMs
        lastTrueMs = estimate.trueMs
        lastAirspeedSource = estimate.source
        lastAirspeedTimestamp = now
    }

    fun updateAveragesAndDisplay(
        currentTime: Long,
        bruttoSample: Double,
        nettoSample: Double,
        thermalActive: Boolean,
        nettoValue: Double,
        nettoValid: Boolean
    ): AverageOutputs {
        val timeWentBack = currentTime < lastBruttoSampleTime || currentTime < lastNettoSampleTime
        val thermalToggled = thermalActive != lastThermalState
        if (timeWentBack || thermalToggled) {
            resetAverageWindows(bruttoSample, nettoSample, currentTime)
        } else {
            lastBruttoSampleTime = addSamplesForElapsedSeconds(
                window = bruttoAverageWindow,
                lastTimestamp = lastBruttoSampleTime,
                currentTime = currentTime,
                sampleValue = bruttoSample
            )
            lastNettoSampleTime = addSamplesForElapsedSeconds(
                window = nettoAverageWindow,
                lastTimestamp = lastNettoSampleTime,
                currentTime = currentTime,
                sampleValue = nettoSample
            )
        }
        lastThermalState = thermalActive

        if (nettoValid) {
            lastNettoValue = nettoValue
            nettoDisplayWindow.addSample(currentTime, nettoValue)
        } else if (!lastNettoValue.isNaN()) {
            nettoDisplayWindow.addSample(currentTime, lastNettoValue)
        }

        val bruttoAverage30s = bruttoAverageWindow.average()
        val bruttoAverage30sValid = bruttoAverage30s.isFinite()
        val nettoAverage30s = nettoAverageWindow.average()
        val rawDisplayNetto = if (!nettoDisplayWindow.isEmpty()) {
            nettoDisplayWindow.average()
        } else {
            nettoValue
        }

        return AverageOutputs(
            bruttoAverage30s = bruttoAverage30s,
            bruttoAverage30sValid = bruttoAverage30sValid,
            nettoAverage30s = nettoAverage30s,
            displayNettoRaw = rawDisplayNetto
        )
    }

    private fun resetAverageWindows(bruttoSample: Double, nettoSample: Double, timestamp: Long) {
        bruttoAverageWindow.clear()
        nettoAverageWindow.clear()
        if (bruttoSample.isFinite()) bruttoAverageWindow.addSample(bruttoSample)
        if (nettoSample.isFinite()) nettoAverageWindow.addSample(nettoSample)
        lastBruttoSampleTime = timestamp
        lastNettoSampleTime = timestamp
    }

    fun resetAll() {
        bruttoAverageWindow.clear()
        nettoAverageWindow.clear()
        nettoDisplayWindow.clear()
        lastBruttoSampleTime = 0L
        lastNettoSampleTime = 0L
        lastThermalState = false
        lastNettoValue = Double.NaN
        lastIndicatedMs = Double.NaN
        lastTrueMs = Double.NaN
        lastAirspeedSource = AirspeedSource.GPS_GROUND
        lastAirspeedTimestamp = 0L
    }
}
