package com.trust3.xcpro.sensors.domain

import com.trust3.xcpro.core.flight.calculations.BarometricAltitudeData
import com.trust3.xcpro.sensors.FixedSampleAverageWindow
import com.trust3.xcpro.sensors.TimedAverageWindow
import com.trust3.xcpro.sensors.addSamplesForElapsedSeconds
import com.trust3.xcpro.sensors.domain.AirspeedEstimate
import com.trust3.xcpro.sensors.domain.AirspeedSource
import kotlin.math.abs

/**
 * Blackboard/fusion helper that owns mutable sensor/fusion state (windows, deltas, QNH jump)
 * and exposes read-only aggregates to the use case.
 */
internal class FusionBlackboard {
    private val bruttoAverageWindow = FixedSampleAverageWindow(FlightMetricsConstants.AVERAGE_WINDOW_SECONDS)
    private val nettoAverageWindow = FixedSampleAverageWindow(FlightMetricsConstants.AVERAGE_WINDOW_SECONDS)
    private val nettoDisplayWindow = TimedAverageWindow(FlightMetricsConstants.NETTO_DISPLAY_WINDOW_MS)

    private var lastBruttoSampleTime = -1L
    private var lastNettoSampleTime = -1L
    private var lastThermalState = false

    private var lastNettoValue = Double.NaN
    private var lastValidNettoSampleTime = -1L

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
        val nettoAverage30sValid: Boolean,
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
        val ageMs = now - lastAirspeedTimestamp
        val withinHold = ageMs in 0..FlightMetricsConstants.SPEED_HOLD_MS
        val canHold = when (lastAirspeedSource) {
            AirspeedSource.EXTERNAL -> lastTrueMs.isFinite()
            else -> lastIndicatedMs.isFinite() && lastTrueMs.isFinite()
        }
        return if (withinHold && canHold) {
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
        tc30TimeMillis: Long,
        bruttoSample: Double,
        nettoSample: Double,
        thermalActive: Boolean,
        nettoValue: Double,
        nettoValid: Boolean
    ): AverageOutputs {
        val safeBruttoSample = if (bruttoSample.isFinite()) bruttoSample else 0.0
        val safeNettoSample = if (nettoSample.isFinite()) nettoSample else 0.0
        val timeWentBack = tc30TimeMillis < lastBruttoSampleTime || tc30TimeMillis < lastNettoSampleTime
        val thermalToggled = thermalActive != lastThermalState
        if (timeWentBack || thermalToggled) {
            resetAverageWindows(safeBruttoSample, safeNettoSample, tc30TimeMillis, nettoValid)
        } else {
            // Keep the window moving even when samples are non-finite.
            lastBruttoSampleTime = addSamplesForElapsedSeconds(
                window = bruttoAverageWindow,
                lastTimestamp = lastBruttoSampleTime,
                currentTime = tc30TimeMillis,
                sampleValue = safeBruttoSample
            )
            lastNettoSampleTime = addSamplesForElapsedSeconds(
                window = nettoAverageWindow,
                lastTimestamp = lastNettoSampleTime,
                currentTime = tc30TimeMillis,
                sampleValue = safeNettoSample
            )
        }
        if (nettoValid) {
            lastValidNettoSampleTime = tc30TimeMillis
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
        val nettoAverage30sValid = nettoAverage30s.isFinite() &&
            lastValidNettoSampleTime >= 0L &&
            (tc30TimeMillis - lastValidNettoSampleTime) in 0..(FlightMetricsConstants.AVERAGE_WINDOW_SECONDS * 1_000L)
        val rawDisplayNetto = if (!nettoDisplayWindow.isEmpty()) {
            nettoDisplayWindow.average()
        } else {
            nettoValue
        }

        return AverageOutputs(
            bruttoAverage30s = bruttoAverage30s,
            bruttoAverage30sValid = bruttoAverage30sValid,
            nettoAverage30s = nettoAverage30s,
            nettoAverage30sValid = nettoAverage30sValid,
            displayNettoRaw = rawDisplayNetto
        )
    }

    private fun resetAverageWindows(
        bruttoSample: Double,
        nettoSample: Double,
        timestamp: Long,
        nettoValid: Boolean
    ) {
        bruttoAverageWindow.clear()
        nettoAverageWindow.clear()
        bruttoAverageWindow.addSample(bruttoSample)
        nettoAverageWindow.addSample(nettoSample)
        lastBruttoSampleTime = timestamp
        lastNettoSampleTime = timestamp
        lastValidNettoSampleTime = if (nettoValid) timestamp else -1L
    }

    fun resetAll() {
        bruttoAverageWindow.clear()
        nettoAverageWindow.clear()
        nettoDisplayWindow.clear()
        lastBruttoSampleTime = -1L
        lastNettoSampleTime = -1L
        lastThermalState = false
        lastNettoValue = Double.NaN
        lastValidNettoSampleTime = -1L
        lastIndicatedMs = Double.NaN
        lastTrueMs = Double.NaN
        lastAirspeedSource = AirspeedSource.GPS_GROUND
        lastAirspeedTimestamp = 0L
    }
}
