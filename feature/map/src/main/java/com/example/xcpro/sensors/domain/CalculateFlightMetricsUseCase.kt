package com.example.xcpro.sensors.domain

import com.example.dfcards.calculations.BarometricAltitudeData
import com.example.dfcards.calculations.ConfidenceLevel
import com.example.dfcards.filters.ModernVarioResult
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.CirclingDetector
import com.example.xcpro.sensors.FixedSampleAverageWindow
import com.example.xcpro.sensors.FlightCalculationHelpers
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.sensors.TimedAverageWindow
import com.example.xcpro.sensors.addSamplesForElapsedSeconds
import com.example.xcpro.weather.wind.data.WindState
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindVector
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure domain use case that translates fused sensor samples into flight metrics.
 * Owns TE/netto smoothing windows so results remain deterministic and testable.
 */
internal class CalculateFlightMetricsUseCase(
    private val flightHelpers: FlightCalculationHelpers,
    private val sinkProvider: StillAirSinkProvider
) {

    private val bruttoAverageWindow = FixedSampleAverageWindow(AVERAGE_WINDOW_SECONDS)
    private val nettoAverageWindow = FixedSampleAverageWindow(AVERAGE_WINDOW_SECONDS)
    private val nettoDisplayWindow = TimedAverageWindow(NETTO_DISPLAY_WINDOW_MS)
    private val circlingDetector = CirclingDetector()

    private var displayVarioState = 0.0
    private var displayNettoState = 0.0
    private var lastBruttoValue = 0.0
    private var lastBruttoSampleTime = 0L
    private var lastNettoSampleTime = 0L
    private var lastNettoValue = Double.NaN
    private var lastThermalState = false
    private var previousGpsSpeed = 0.0
    private var lastIndicatedMs = Double.NaN
    private var lastTrueMs = Double.NaN
    private var lastAirspeedSource = AirspeedSource.GPS_GROUND
    private var lastAirspeedTimestamp = 0L

    fun execute(request: FlightMetricsRequest): FlightMetricsResult {
        val gps = request.gps
        val currentTime = request.currentTimeMillis
        val varioResult = request.varioResult

        val rawVerticalSpeed = varioResult.verticalSpeed
        val currentSpeed = gps.speed.value
        val teVerticalSpeed = flightHelpers.calculateTotalEnergy(
            rawVario = rawVerticalSpeed,
            currentSpeed = currentSpeed,
            previousSpeed = previousGpsSpeed,
            deltaTime = request.deltaTimeSeconds
        )
        previousGpsSpeed = currentSpeed

        val teVario = teVerticalSpeed.takeIf { currentTime <= request.varioValidUntil }
        val gpsVario = request.varioGpsValue
        val varioSource = if (teVario != null) "TE" else "GPS"
        val bruttoVario = when {
            teVario != null -> teVario
            gpsVario.isFinite() -> gpsVario
            else -> lastBruttoValue  // hold last valid brutto if both missing
        }
        val varioValid = bruttoVario.isFinite()
        if (bruttoVario.isFinite()) lastBruttoValue = bruttoVario

        val baroAltitude = varioResult.altitude
        val baroResult = request.baroResult
        val qnh = baroResult?.qnh ?: DEFAULT_QNH_HPA
        val isQnhCalibrated = baroResult?.isCalibrated ?: false
        val pressureAltitude = baroResult?.pressureAltitudeMeters ?: baroAltitude
        val baroGpsDelta = baroResult?.gpsDeltaMeters
            ?: gps.altitude.value.takeIf { !it.isNaN() }?.let { baroAltitude - it }
        val baroConfidence = baroResult?.confidenceLevel ?: ConfidenceLevel.LOW
        val qnhCalibrationAgeSeconds = baroResult?.lastCalibrationTime?.takeIf { it > 0L }?.let {
            val delta = (currentTime - it) / 1000L
            if (delta < 0) 0L else delta
        } ?: -1L

        val windState = request.windState
        val windVector = windState?.vector

        val isCircling = circlingDetector.update(
            trackDegrees = gps.bearing,
            timestampMillis = gps.timestamp,
            groundSpeed = gps.speed.value
        )

        // Keep helper-driven state (AGL, LD, thermal averages) up to date.
        flightHelpers.updateAGL(baroAltitude, gps, gps.speed.value)
        flightHelpers.recordLocationSample(gps)
        val calculatedLD = flightHelpers.calculateCurrentLD(gps, baroAltitude)

        val airspeedFromWind = estimateFromWind(
            gpsSpeed = gps.speed.value,
            gpsBearingDeg = gps.bearing,
            altitudeMeters = altitudeForAirspeed(baroAltitude, gps.altitude.value),
            qnhHpa = qnh,
            windVector = windVector
        )

        val nettoResult = flightHelpers.calculateNetto(
            currentVerticalSpeed = bruttoVario,
            trueAirspeed = airspeedFromWind?.trueMs,
            fallbackGroundSpeed = gps.speed.value
        )
        val nettoSampleValue = resolveNettoSampleValue(nettoResult.value, nettoResult.valid)

        updateAverageWindows(
            currentTime = currentTime,
            bruttoSample = bruttoVario,
            nettoSample = nettoSampleValue,
            thermalActive = isCircling
        )

        if (nettoResult.valid) {
            lastNettoValue = nettoResult.value
            nettoDisplayWindow.addSample(currentTime, nettoResult.value)
        } else {
            // XCSoar keeps publishing last known netto through brief dropouts
            if (!lastNettoValue.isNaN()) {
                nettoDisplayWindow.addSample(currentTime, lastNettoValue)
            }
        }

        val bruttoAverage30s = bruttoAverageWindow.average()
        val nettoAverage30s = nettoAverageWindow.average()
        val displayVario = smoothDisplayVario(bruttoVario, request.deltaTimeSeconds, varioValid)
        val rawDisplayNetto = if (!nettoDisplayWindow.isEmpty()) {
            nettoDisplayWindow.average()
        } else {
            nettoResult.value
        }
        val displayNetto = smoothDisplayNetto(rawDisplayNetto, request.deltaTimeSeconds, nettoResult.valid)

        val altitudeForAirspeed = altitudeForAirspeed(baroAltitude, gps.altitude.value)
        val hasMotion = gps.speed.value.isFinite() && gps.speed.value > MIN_MOVING_SPEED_MS
        val airspeedEstimate = airspeedFromWind ?: if (nettoResult.valid && hasMotion) {
            estimateFromPolarSink(
                netto = nettoResult.value.toFloat(),
                verticalSpeed = bruttoVario,
                altitudeMeters = altitudeForAirspeed,
                qnhHpa = qnh
            )
        } else {
            null
        }
        val now = currentTime
        val activeEstimate = airspeedEstimate ?: run {
            if (now - lastAirspeedTimestamp <= SPEED_HOLD_MS &&
                lastIndicatedMs.isFinite() && lastTrueMs.isFinite()
            ) {
                AirspeedEstimate(lastIndicatedMs, lastTrueMs, lastAirspeedSource)
            } else null
        } ?: AirspeedEstimate(
            indicatedMs = gps.speed.value.takeIf { it.isFinite() } ?: 0.0,
            trueMs = gps.speed.value.takeIf { it.isFinite() } ?: 0.0,
            source = AirspeedSource.GPS_GROUND
        )

        val indicatedAirspeedMs = activeEstimate.indicatedMs
        val trueAirspeedMs = activeEstimate.trueMs
        val airspeedSourceLabel = activeEstimate.source.label
        val tasValid = when (activeEstimate.source) {
            AirspeedSource.GPS_GROUND -> gps.speed.value.isFinite() && gps.speed.value > MIN_MOVING_SPEED_MS
            else -> true
        }

        // Remember last valid airspeed for hold
        if (airspeedEstimate != null) {
            lastIndicatedMs = airspeedEstimate.indicatedMs
            lastTrueMs = airspeedEstimate.trueMs
            lastAirspeedSource = airspeedEstimate.source
            lastAirspeedTimestamp = now
        }

        val teAltitude = computeTotalEnergyAltitude(baroAltitude, trueAirspeedMs)
        flightHelpers.updateThermalState(
            timestampMillis = currentTime,
            teAltitudeMeters = teAltitude,
            verticalSpeedMs = bruttoVario,
            isCircling = isCircling
        )
        val thermalAvgCircle = flightHelpers.thermalAverageCurrent
        val thermalAvgTotal = flightHelpers.thermalAverageTotal
        val thermalAvg30s = flightHelpers.thermalAverage30s
        val thermalAvg30sValid = flightHelpers.thermalAverage30sValid
        val thermalGain = flightHelpers.thermalGainCurrent
        val thermalGainValid = flightHelpers.thermalGainValid

        val windSpeedValue = windVector?.speed?.toFloat() ?: 0f
        val windDirectionFrom = windVector?.directionFromDeg?.toFloat() ?: 0f
        val windHeadwind = windState?.headwind ?: 0.0
        val windCrosswind = windState?.crosswind ?: 0.0
        val windQuality = windState?.quality ?: 0
        val windSource = windState?.source ?: WindSource.NONE

        return FlightMetricsResult(
            baroAltitude = baroAltitude,
            qnh = qnh,
            isQnhCalibrated = isQnhCalibrated,
            pressureAltitude = pressureAltitude,
            baroGpsDelta = baroGpsDelta,
            baroConfidence = baroConfidence,
            qnhCalibrationAgeSeconds = qnhCalibrationAgeSeconds,
            bruttoVario = bruttoVario,
            verticalSpeed = bruttoVario,
            varioSource = varioSource,
            varioValid = varioValid,
            teVario = teVario,
            bruttoAverage30s = bruttoAverage30s,
            nettoAverage30s = nettoAverage30s,
            displayVario = displayVario,
            displayNetto = displayNetto,
            netto = nettoResult.value.toFloat(),
            nettoValid = nettoResult.valid,
            indicatedAirspeedMs = indicatedAirspeedMs,
            trueAirspeedMs = trueAirspeedMs,
            airspeedSourceLabel = airspeedSourceLabel,
            tasValid = tasValid,
            thermalAverageCircle = thermalAvgCircle,
            thermalAverage30s = thermalAvg30s,
            thermalAverageTotal = thermalAvgTotal,
            thermalGain = thermalGain,
            thermalGainValid = thermalGainValid,
            calculatedLD = calculatedLD,
            windSpeedValue = windSpeedValue,
            windDirectionFrom = windDirectionFrom,
            windHeadwind = windHeadwind,
            windCrosswind = windCrosswind,
            windQuality = windQuality,
            windSource = windSource,
            teAltitude = teAltitude,
            isCircling = isCircling,
            thermalAverage30sValid = thermalAvg30sValid
        )
    }

    fun reset() {
        bruttoAverageWindow.clear()
        nettoAverageWindow.clear()
        nettoDisplayWindow.clear()
        displayVarioState = 0.0
        displayNettoState = 0.0
        lastBruttoSampleTime = 0L
        lastNettoSampleTime = 0L
        lastNettoValue = Double.NaN
        lastThermalState = false
        previousGpsSpeed = 0.0
        circlingDetector.reset()
    }

    private fun altitudeForAirspeed(baroAltitude: Double, gpsAltitude: Double): Double = when {
        baroAltitude.isFinite() && baroAltitude != 0.0 -> baroAltitude
        gpsAltitude.isFinite() -> gpsAltitude
        else -> 0.0
    }

    private fun estimateFromWind(
        gpsSpeed: Double,
        gpsBearingDeg: Double,
        altitudeMeters: Double,
        qnhHpa: Double,
        windVector: WindVector?
    ): AirspeedEstimate? {
        if (windVector == null || !gpsSpeed.isFinite() || gpsSpeed <= 0.1) return null
        if (!gpsBearingDeg.isFinite()) return null
        val bearingRad = Math.toRadians(gpsBearingDeg)
        val groundEast = gpsSpeed * sin(bearingRad)
        val groundNorth = gpsSpeed * cos(bearingRad)
        val tasEast = groundEast + windVector.east
        val tasNorth = groundNorth + windVector.north
        val tas = hypot(tasEast, tasNorth)
        if (!tas.isFinite() || tas <= 0.1) return null
        val densityRatio = computeDensityRatio(altitudeMeters, qnhHpa)
        val indicated = if (densityRatio > 0.0) tas * sqrt(densityRatio) else tas
        return AirspeedEstimate(indicatedMs = indicated, trueMs = tas, source = AirspeedSource.WIND_VECTOR)
    }

    private fun estimateFromPolarSink(
        netto: Float,
        verticalSpeed: Double,
        altitudeMeters: Double,
        qnhHpa: Double
    ): AirspeedEstimate? {
        val sinkEstimate = abs(netto.toDouble() - verticalSpeed)
        if (!sinkEstimate.isFinite() || sinkEstimate < MIN_SINK_FOR_IAS_MS) {
            return null
        }
        val tasMs = findSpeedForSink(sinkEstimate) ?: return null
        val densityRatio = computeDensityRatio(altitudeMeters, qnhHpa)
        val indicatedMs = if (densityRatio > 0.0) tasMs * sqrt(densityRatio) else tasMs
        return AirspeedEstimate(indicatedMs = indicatedMs, trueMs = tasMs, source = AirspeedSource.POLAR_SINK)
    }

    private fun findSpeedForSink(targetSinkMs: Double): Double? {
        var speed = IAS_SCAN_MIN_MS
        var bestSpeed: Double? = null
        var bestError = Double.POSITIVE_INFINITY
        while (speed <= IAS_SCAN_MAX_MS) {
            val sink = sinkProvider.sinkAtSpeed(speed) ?: break
            val error = abs(sink - targetSinkMs)
            if (error < bestError) {
                bestError = error
                bestSpeed = speed
            }
            speed += IAS_SCAN_STEP_MS
        }
        return bestSpeed
    }

    private fun computeTotalEnergyAltitude(baroAltitude: Double, trueAirspeed: Double): Double {
        val potential = if (baroAltitude.isFinite()) baroAltitude else 0.0
        val kinetic = if (trueAirspeed.isFinite()) {
            (trueAirspeed * trueAirspeed) / (2.0 * GRAVITY)
        } else {
            0.0
        }
        return potential + kinetic
    }

    private fun computeDensityRatio(altitudeMeters: Double, qnhHpa: Double): Double {
        val tempSeaLevelK = SEA_LEVEL_TEMP_CELSIUS + 273.15
        val theta = 1.0 + (TEMP_LAPSE_RATE_C_PER_M * altitudeMeters) / tempSeaLevelK
        if (theta <= 0.0) return 0.0
        // ISA troposphere: rho/ρ0 = theta^(-g/(R*L) - 1), with L negative
        val exponent = (-GRAVITY / (GAS_CONSTANT * TEMP_LAPSE_RATE_C_PER_M)) - 1.0
        return theta.pow(exponent)
    }

    private fun smoothDisplayVario(raw: Double, deltaTime: Double, isValid: Boolean): Double {
        val targetAlpha = (deltaTime / DISPLAY_SMOOTH_TIME_S).coerceIn(0.0, 1.0)
        displayVarioState += targetAlpha * (raw - displayVarioState)
        if (!isValid) {
            displayVarioState *= DISPLAY_DECAY_FACTOR
        }
        if (!displayVarioState.isFinite()) {
            displayVarioState = 0.0
        }
        return displayVarioState.coerceIn(-DISPLAY_VAR_CLAMP, DISPLAY_VAR_CLAMP)
    }

    private fun smoothDisplayNetto(raw: Double, deltaTime: Double, isValid: Boolean): Double {
        val targetAlpha = (deltaTime / DISPLAY_SMOOTH_TIME_S).coerceIn(0.0, 1.0)
        displayNettoState += targetAlpha * (raw - displayNettoState)
        if (!isValid) {
            displayNettoState *= DISPLAY_DECAY_FACTOR
        }
        if (!displayNettoState.isFinite()) {
            displayNettoState = 0.0
        }
        return displayNettoState.coerceIn(-DISPLAY_VAR_CLAMP, DISPLAY_VAR_CLAMP)
    }

    private fun updateAverageWindows(
        currentTime: Long,
        bruttoSample: Double,
        nettoSample: Double,
        thermalActive: Boolean
    ) {
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
    }

    private fun resetAverageWindows(bruttoSample: Double, nettoSample: Double, timestamp: Long) {
        bruttoAverageWindow.seed(bruttoSample)
        nettoAverageWindow.seed(nettoSample)
        lastBruttoSampleTime = timestamp
        lastNettoSampleTime = timestamp
    }

    private fun resolveNettoSampleValue(rawNetto: Double, nettoValid: Boolean): Double {
        if (nettoValid) {
            return rawNetto
        }
        val fallback = lastNettoValue
        return if (!fallback.isNaN()) fallback else rawNetto
    }

    companion object {
        private const val DEFAULT_QNH_HPA = 1013.25
        private const val AVERAGE_WINDOW_SECONDS = 30
        private const val NETTO_DISPLAY_WINDOW_MS = 5_000L
        private const val DISPLAY_VAR_CLAMP = 7.0
        private const val DISPLAY_SMOOTH_TIME_S = 0.6
        private const val DISPLAY_DECAY_FACTOR = 0.9
        private const val MIN_SINK_FOR_IAS_MS = 0.15
        private const val IAS_SCAN_MIN_MS = 8.0
        private const val IAS_SCAN_MAX_MS = 80.0
        private const val IAS_SCAN_STEP_MS = 0.5
        private const val SEA_LEVEL_PRESSURE_HPA = 1013.25
        private const val SEA_LEVEL_TEMP_CELSIUS = 15.0
        private const val TEMP_LAPSE_RATE_C_PER_M = -0.0065
        private const val GAS_CONSTANT = 287.05
        private const val GRAVITY = 9.80665
        private const val SPEED_HOLD_MS = 10_000L
        private const val MIN_MOVING_SPEED_MS = 0.5
    }
}

data class FlightMetricsRequest(
    val gps: GPSData,
    val currentTimeMillis: Long,
    val deltaTimeSeconds: Double,
    val varioResult: ModernVarioResult,
    val varioGpsValue: Double,
    val baroResult: BarometricAltitudeData?,
    val windState: WindState?,
    val varioValidUntil: Long
)

data class FlightMetricsResult(
    val baroAltitude: Double,
    val qnh: Double,
    val isQnhCalibrated: Boolean,
    val pressureAltitude: Double,
    val baroGpsDelta: Double?,
    val baroConfidence: ConfidenceLevel,
    val qnhCalibrationAgeSeconds: Long,
    val bruttoVario: Double,
    val verticalSpeed: Double,
    val varioSource: String,
    val varioValid: Boolean,
    val teVario: Double?,
    val bruttoAverage30s: Double,
    val nettoAverage30s: Double,
    val displayVario: Double,
    val displayNetto: Double,
    val netto: Float,
    val nettoValid: Boolean,
    val indicatedAirspeedMs: Double,
    val trueAirspeedMs: Double,
    val airspeedSourceLabel: String,
    val tasValid: Boolean,
    val thermalAverageCircle: Float,
    val thermalAverage30s: Float,
    val thermalAverageTotal: Float,
    val thermalGain: Double,
    val thermalGainValid: Boolean,
    val calculatedLD: Float,
    val windSpeedValue: Float,
    val windDirectionFrom: Float,
    val windHeadwind: Double,
    val windCrosswind: Double,
    val windQuality: Int,
    val windSource: WindSource,
    val teAltitude: Double,
    val isCircling: Boolean,
    val thermalAverage30sValid: Boolean
)
