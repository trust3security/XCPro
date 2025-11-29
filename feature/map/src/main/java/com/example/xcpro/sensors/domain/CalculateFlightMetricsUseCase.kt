package com.example.xcpro.sensors.domain
import com.example.xcpro.sensors.DisplayVarioSmoother
import com.example.xcpro.sensors.domain.FusionBlackboard
import com.example.dfcards.calculations.BarometricAltitudeData
import com.example.dfcards.calculations.ConfidenceLevel
import com.example.dfcards.filters.ModernVarioResult
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.CirclingDetector
import com.example.xcpro.sensors.FlightCalculationHelpers
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.weather.wind.data.WindState
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindVector
import com.example.xcpro.sensors.domain.AirspeedEstimate
import com.example.xcpro.sensors.domain.AirspeedSource
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_DECAY_FACTOR
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_SMOOTH_TIME_S
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_VAR_CLAMP
import com.example.xcpro.sensors.domain.FlightMetricsConstants.GRAVITY
import com.example.xcpro.sensors.domain.FlightMetricsConstants.SPEED_HOLD_MS
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DEFAULT_QNH_HPA
import com.example.xcpro.sensors.domain.WindEstimator


/**
 * Pure domain use case that translates fused sensor samples into flight metrics.
 * Owns TE/netto smoothing windows so results remain deterministic and testable.
 */
internal class CalculateFlightMetricsUseCase(
    private val flightHelpers: FlightCalculationHelpers,
    private val sinkProvider: StillAirSinkProvider,
    private val windEstimator: WindEstimator
) {
    // Allow choosing whether nav altitude uses baro (XCSoar-style). Default true.
    var navBaroAltitudeEnabled: Boolean = true

    private val circlingDetector = CirclingDetector()
    private val fusionBlackboard = FusionBlackboard()

    private val displaySmoother = DisplayVarioSmoother(
        smoothTimeSeconds = DISPLAY_SMOOTH_TIME_S,
        decayFactor = DISPLAY_DECAY_FACTOR,
        clamp = DISPLAY_VAR_CLAMP
    )
    private var prevTeSpeed: Double = 0.0

    fun execute(request: FlightMetricsRequest): FlightMetricsResult {
        val gps = request.gps
        val currentTime = request.currentTimeMillis
        val varioResult = request.varioResult

        // QNH-agnostic vertical speed from pressure deltas
        val pressureAltitudeInput = request.baroResult?.pressureAltitudeMeters ?: varioResult.altitude
        val rawBaroVario = fusionBlackboard.pressureVario(pressureAltitudeInput, currentTime)
        val baroVario = fusionBlackboard.baroVario(varioResult.altitude, currentTime)
        val gpsAlt = gps.altitude.value
        val gpsAltVario = fusionBlackboard.gpsVario(gpsAlt, currentTime)

        val rawVerticalSpeed = when {
            rawBaroVario.isFinite() -> rawBaroVario
            baroVario.isFinite() -> baroVario
            gpsAltVario.isFinite() -> gpsAltVario
            varioResult.verticalSpeed.isFinite() -> varioResult.verticalSpeed
            else -> fusionBlackboard.bruttoFallback()
        }

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
        val calibrationChanged = fusionBlackboard.detectCalibrationChange(qnh, baroResult, currentTime)
        if (calibrationChanged) {
            flightHelpers.resetThermalTracking()
        }

        val windState = request.windState
        val windVector = windState?.vector

        val airspeedFromWind = windEstimator.fromWind(
            gpsSpeed = gps.speed.value,
            gpsBearingDeg = gps.bearing,
            altitudeMeters = altitudeForAirspeed(baroAltitude, gps.altitude.value),
            qnhHpa = qnh,
            windVector = windVector
        )

        val teSpeed = airspeedFromWind?.trueMs ?: 0.0
        val teVerticalSpeed = flightHelpers.calculateTotalEnergy(
            rawVario = rawVerticalSpeed,
            currentSpeed = teSpeed,
            previousSpeed = prevTeSpeed,
            deltaTime = request.deltaTimeSeconds
        )
        prevTeSpeed = teSpeed

        val gpsVario = gpsAltVario

        val teVario = teVerticalSpeed.takeIf { currentTime <= request.varioValidUntil }
        val bruttoVario = when {
            teVario != null -> teVario
            gpsVario.isFinite() -> gpsVario
            else -> Double.NaN  // prefer dropping the sample over holding stale values
        }
        val varioSource = when {
            teVario != null -> "TE"
            gpsVario.isFinite() -> "GPS"
            else -> "NONE"
        }
        val varioValid = bruttoVario.isFinite()
        fusionBlackboard.rememberBrutto(bruttoVario)

        val isCircling = circlingDetector.update(
            trackDegrees = gps.bearing,
            timestampMillis = gps.timestamp,
            groundSpeed = gps.speed.value
        )

        // Keep helper-driven state (AGL, LD, thermal averages) up to date.
        flightHelpers.updateAGL(baroAltitude, gps, gps.speed.value)
        flightHelpers.recordLocationSample(gps)
        val calculatedLD = flightHelpers.calculateCurrentLD(gps, baroAltitude)

        val nettoResult = flightHelpers.calculateNetto(
            currentVerticalSpeed = bruttoVario,
            trueAirspeed = airspeedFromWind?.trueMs,
            fallbackGroundSpeed = gps.speed.value
        )
        val nettoSampleValue = fusionBlackboard.resolveNettoSampleValue(nettoResult.value, nettoResult.valid)

        // feed 30s windows with QNH-agnostic sample; reset on circling or calibration change
        val avgVarioSample = rawBaroVario.takeIf { it.isFinite() } ?: bruttoVario
        val averages = fusionBlackboard.updateAveragesAndDisplay(
            currentTime = currentTime,
            bruttoSample = avgVarioSample,
            nettoSample = nettoSampleValue,
            thermalActive = isCircling,
            nettoValue = nettoResult.value,
            nettoValid = nettoResult.valid
        )

        val bruttoAverage30s = averages.bruttoAverage30s
        val bruttoAverage30sValid = averages.bruttoAverage30sValid
        val nettoAverage30s = averages.nettoAverage30s
        val displayVario = smoothDisplayVario(bruttoVario, request.deltaTimeSeconds, varioValid)
        val rawDisplayNetto = averages.displayNettoRaw
        val displayNetto = smoothDisplayNetto(rawDisplayNetto, request.deltaTimeSeconds, nettoResult.valid)

        val navAltitude = when {
            navBaroAltitudeEnabled && baroResult != null && isQnhCalibrated -> baroAltitude
            gps.altitude.value.isFinite() -> gps.altitude.value
            else -> baroAltitude
        }

        val altitudeForAirspeed = altitudeForAirspeed(navAltitude, gps.altitude.value)
        val airspeedEstimate = airspeedFromWind
        val now = currentTime
        val activeEstimate = fusionBlackboard.resolveAirspeedHold(airspeedEstimate, now)

        val indicatedAirspeedMs = activeEstimate?.indicatedMs ?: 0.0
        val trueAirspeedMs = activeEstimate?.trueMs ?: 0.0
        val airspeedSourceLabel = activeEstimate?.source?.label ?: AirspeedSource.GPS_GROUND.label
        val tasValid = activeEstimate != null

        val teAltitude = computeTotalEnergyAltitude(navAltitude, trueAirspeedMs)
        if (!calibrationChanged) {
            flightHelpers.updateThermalState(
                timestampMillis = currentTime,
                teAltitudeMeters = teAltitude,
                verticalSpeedMs = bruttoVario,
                isCircling = isCircling
            )
        }
        val thermalAvgCircle = flightHelpers.thermalAverageCurrent
        val thermalAvgTotal = flightHelpers.thermalAverageTotal
        val thermalAvg30s = bruttoAverage30s.toFloat()
        val thermalAvg30sValid = bruttoAverage30sValid
        val thermalGain = flightHelpers.thermalGainCurrent
        val thermalGainValid = flightHelpers.thermalGainValid
        val currentThermalLift = flightHelpers.currentThermalLiftRate
        val currentThermalValid = flightHelpers.currentThermalValid

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
            navAltitude = navAltitude,
            bruttoAverage30s = bruttoAverage30s,
            nettoAverage30s = nettoAverage30s,
            bruttoAverage30sValid = bruttoAverage30sValid,
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
            currentThermalLiftRate = currentThermalLift,
            currentThermalValid = currentThermalValid,
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
        fusionBlackboard.resetAll()
        displaySmoother.reset()
        circlingDetector.reset()
        prevTeSpeed = 0.0
    }

    private fun altitudeForAirspeed(baroAltitude: Double, gpsAltitude: Double): Double = when {
        baroAltitude.isFinite() && baroAltitude != 0.0 -> baroAltitude
        gpsAltitude.isFinite() -> gpsAltitude
        else -> 0.0
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

    private fun smoothDisplayVario(raw: Double, deltaTime: Double, isValid: Boolean): Double =
        displaySmoother.smoothVario(raw, deltaTime, isValid)

    private fun smoothDisplayNetto(raw: Double, deltaTime: Double, isValid: Boolean): Double =
        displaySmoother.smoothNetto(raw, deltaTime, isValid)

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
        private const val QNH_JUMP_THRESHOLD_HPA = 0.5
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
    val navAltitude: Double,
    val bruttoAverage30s: Double,
    val bruttoAverage30sValid: Boolean,
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
    val currentThermalLiftRate: Double,
    val currentThermalValid: Boolean,
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
