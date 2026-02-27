package com.example.xcpro.sensors.domain
import com.example.xcpro.sensors.DisplayVarioSmoother
import com.example.xcpro.sensors.NeedleVarioDynamics
import com.example.xcpro.sensors.domain.FusionBlackboard
import com.example.dfcards.calculations.BarometricAltitudeData
import com.example.dfcards.calculations.ConfidenceLevel
import com.example.dfcards.filters.ModernVarioResult
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.CirclingDetector
import com.example.xcpro.sensors.FlightCalculationHelpers
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.sensors.domain.AirspeedEstimate
import com.example.xcpro.sensors.domain.AirspeedSource
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_DECAY_FACTOR
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_SMOOTH_TIME_S
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_VAR_CLAMP
import com.example.xcpro.sensors.domain.FlightMetricsConstants.FAST_NEEDLE_T95_SECONDS
import com.example.xcpro.sensors.domain.FlightMetricsConstants.NEEDLE_VAR_CLAMP
import com.example.xcpro.sensors.domain.FlightMetricsConstants.NEEDLE_T95_SECONDS
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DEFAULT_QNH_HPA
import com.example.xcpro.sensors.domain.WindEstimator
import com.example.xcpro.sensors.domain.SensorFrontEnd.SensorSnapshot
import com.example.xcpro.common.flight.FlightMode
import kotlin.math.abs


/**
 * Pure domain use case that translates fused sensor samples into flight metrics.
 * Owns TE/netto smoothing windows so results remain deterministic and testable.
 */
internal class CalculateFlightMetricsUseCase(
    private val flightHelpers: FlightCalculationHelpers,
    private val sinkProvider: StillAirSinkProvider,
    private val windEstimator: WindEstimator
) {
    // Allow choosing whether nav altitude uses baro. Default true.
    var navBaroAltitudeEnabled: Boolean = true

    private val circlingDetector = CirclingDetector()
    private val fusionBlackboard = FusionBlackboard()
    private val sensorFrontEnd = SensorFrontEnd(fusionBlackboard)
    private val levoNettoCalculator = LevoNettoCalculator(sinkProvider)
    private val autoMcCalculator = AutoMcCalculator()
    private val speedToFlyCalculator = SpeedToFlyCalculator(sinkProvider)

    private val displaySmoother = DisplayVarioSmoother(
        smoothTimeSeconds = DISPLAY_SMOOTH_TIME_S,
        decayFactor = DISPLAY_DECAY_FACTOR,
        clamp = DISPLAY_VAR_CLAMP
    )
    private val baselineDisplaySmoother = DisplayVarioSmoother(
        smoothTimeSeconds = DISPLAY_SMOOTH_TIME_S,
        decayFactor = DISPLAY_DECAY_FACTOR,
        clamp = DISPLAY_VAR_CLAMP
    )
    private val needleDynamics = NeedleVarioDynamics(
        t95Seconds = NEEDLE_T95_SECONDS,
        clamp = NEEDLE_VAR_CLAMP
    )
    private val fastNeedleDynamics = NeedleVarioDynamics(
        t95Seconds = FAST_NEEDLE_T95_SECONDS,
        clamp = DISPLAY_VAR_CLAMP
    )
    private var prevTeSpeed: Double = 0.0
    private var groundZeroAccumulatedSeconds: Double = 0.0

    fun execute(request: FlightMetricsRequest): FlightMetricsResult {
        val gps = request.gps
        val currentTime = request.currentTimeMillis
        val wallTime = request.wallTimeMillis
        val varioResult = request.varioResult

        val baroAltitude = varioResult.altitude
        val baroResult = request.baroResult
        val qnh = baroResult?.qnh ?: DEFAULT_QNH_HPA
        val isQnhCalibrated = baroResult?.isCalibrated ?: false
        val pressureAltitude = baroResult?.pressureAltitudeMeters ?: baroAltitude
        val baroGpsDelta = baroResult?.gpsDeltaMeters
            ?: gps.altitude.value.takeIf { !it.isNaN() }?.let { baroAltitude - it }
        val baroConfidence = baroResult?.confidenceLevel ?: ConfidenceLevel.LOW
        val qnhCalibrationAgeSeconds = baroResult?.lastCalibrationTime?.takeIf { it > 0L }?.let {
            val delta = (wallTime - it) / 1000L
            if (delta < 0) 0L else delta
        } ?: -1L
        val calibrationChanged = fusionBlackboard.detectCalibrationChange(qnh, baroResult, wallTime)
        if (calibrationChanged) {
            flightHelpers.resetThermalTracking()
        }

        val windState = request.windState
        val windConfidence = windState?.confidence ?: 0.0
        val windEligibleForTe = windState?.isAvailable == true && windConfidence >= LEVO_WIND_CONF_MIN
        val windVectorForTe = if (windEligibleForTe) windState?.vector else null

        val externalAirspeed = resolveExternalAirspeed(
            sample = request.externalAirspeedSample,
            currentTimeMillis = currentTime
        )
        val airspeedFromWind = windEstimator.fromWind(
            gpsSpeed = gps.speed.value,
            gpsBearingDeg = gps.bearing,
            altitudeMeters = altitudeForAirspeed(baroAltitude, gps.altitude.value),
            qnhHpa = qnh,
            windVector = windVectorForTe
        )
        val fallbackAirspeed = gps.speed.value
            .takeIf { it.isFinite() && it > MIN_FALLBACK_GPS_SPEED_MS }
            ?.let { speed ->
                AirspeedEstimate(
                    indicatedMs = speed,
                    trueMs = speed,
                    source = AirspeedSource.GPS_GROUND
                )
            }
        val chosenAirspeed = externalAirspeed ?: airspeedFromWind ?: fallbackAirspeed

        val teSpeed = chosenAirspeed?.trueMs
        val teVario = if (
            request.teCompensationEnabled &&
            teSpeed != null &&
            chosenAirspeed.source.energyHeightEligible &&
            teSpeed > TE_MIN_SPEED_MS &&
            prevTeSpeed > TE_MIN_SPEED_MS &&
            request.deltaTimeSeconds > TE_MIN_DT_SECONDS
        ) {
            val teVerticalSpeed = flightHelpers.calculateTotalEnergy(
                rawVario = varioResult.verticalSpeed,
                currentSpeed = teSpeed,
                previousSpeed = prevTeSpeed,
                deltaTime = request.deltaTimeSeconds
            )
            prevTeSpeed = teSpeed
            teVerticalSpeed.takeIf { currentTime <= request.varioValidUntil }
        } else {
            prevTeSpeed = 0.0
            null
        }

        val pressureVarioOverride = varioResult.verticalSpeed.takeIf {
            it.isFinite() && currentTime <= request.varioValidUntil
        }

        val snapshot: SensorSnapshot = sensorFrontEnd.buildSnapshot(
            navBaroAltitudeEnabled = navBaroAltitudeEnabled,
            baroAltitude = baroAltitude,
            gpsAltitude = gps.altitude.value,
            gpsTimestampMillis = request.gpsTimestampMillis,
            baroResult = baroResult,
            isQnhCalibrated = isQnhCalibrated,
            teVario = teVario,
            airspeedEstimate = chosenAirspeed,
            currentTime = currentTime,
            pressureVarioOverride = pressureVarioOverride
        )
        val navAltitude = snapshot.navAltitude
        val indicatedAirspeedMs = snapshot.indicatedAirspeedMs
        val trueAirspeedMs = snapshot.trueAirspeedMs
        val airspeedSourceLabel = snapshot.airspeedSource.label
        val tasValid = snapshot.tasValid
        val teAltitude = snapshot.teAltitude
        val bruttoVario = snapshot.bruttoVario
        val varioSource = snapshot.varioSource
        val varioValid = snapshot.varioValid

        val circlingDecision = circlingDetector.update(
            trackDegrees = gps.bearing,
            timestampMillis = request.gpsTimestampMillis,
            isFlying = request.isFlying
        )
        val isCircling = circlingDecision.isCircling
        val isTurning = circlingDecision.isTurning

        // Keep helper-driven state (AGL, LD, thermal averages) up to date.
        val sampleTimeMillis = if (request.gpsTimestampMillis > 0L) {
            request.gpsTimestampMillis
        } else {
            currentTime
        }
        if (request.allowOnlineTerrainLookup) {
            flightHelpers.updateAGL(baroAltitude, gps, gps.speed.value)
        }
        flightHelpers.recordLocationSample(gps, sampleTimeMillis)
        val calculatedLD = flightHelpers.calculateCurrentLD(gps, baroAltitude, sampleTimeMillis)

        val nettoResult = flightHelpers.calculateNetto(
            currentVerticalSpeed = bruttoVario,
            indicatedAirspeed = indicatedAirspeedMs,
            fallbackGroundSpeed = gps.speed.value,
            timestampMillis = sampleTimeMillis
        )
        val nettoSampleValue = fusionBlackboard.resolveNettoSampleValue(nettoResult.value, nettoResult.valid)

        // feed 30s windows with QNH-agnostic sample; reset on circling or calibration change
        val avgVarioSample = when {
            teVario != null && teVario.isFinite() -> teVario
            snapshot.pressureVario.isFinite() -> snapshot.pressureVario
            snapshot.pressureAltitudeVario.isFinite() -> snapshot.pressureAltitudeVario
            snapshot.gpsVario.isFinite() -> snapshot.gpsVario
            else -> bruttoVario
        }
        val tc30TimeMillis = if (request.gpsTimestampMillis > 0L) {
            request.gpsTimestampMillis
        } else {
            currentTime
        }
        val averages = fusionBlackboard.updateAveragesAndDisplay(
            currentTime = currentTime,
            tc30TimeMillis = tc30TimeMillis,
            bruttoSample = avgVarioSample,
            nettoSample = nettoSampleValue,
            thermalActive = isCircling,
            nettoValue = nettoResult.value,
            nettoValid = nettoResult.valid
        )

        val bruttoAverage30s = averages.bruttoAverage30s
        val bruttoAverage30sValid = averages.bruttoAverage30sValid
        val nettoAverage30s = averages.nettoAverage30s
        val displayVarioRaw = smoothDisplayVario(bruttoVario, request.deltaTimeSeconds, varioValid)
        // Avoid ground-zero clamping while flying; it can force sudden zero snaps in lift.
        val displayVario = if (request.isFlying) {
            displayVarioRaw
        } else {
            applyGroundZeroBias(displayVarioRaw, gps.speed.value, request.deltaTimeSeconds)
        }
        val displayBaselineVario = smoothBaselineDisplayVario(snapshot.baselineVario, request.deltaTimeSeconds, snapshot.baselineVarioValid)
        val rawDisplayNetto = averages.displayNettoRaw
        val displayNetto = smoothDisplayNetto(rawDisplayNetto, request.deltaTimeSeconds, nettoResult.valid)
        val displayNeedleVario = needleDynamics.update(
            target = bruttoVario,
            deltaTimeSeconds = request.deltaTimeSeconds,
            isValid = varioValid
        )
        val displayNeedleVarioFast = fastNeedleDynamics.update(
            target = bruttoVario,
            deltaTimeSeconds = request.deltaTimeSeconds,
            isValid = varioValid
        )
        if (!calibrationChanged) {
            val thermalAltitude = if (
                request.teCompensationEnabled &&
                snapshot.airspeedSource.energyHeightEligible
            ) {
                teAltitude
            } else {
                navAltitude
            }
            flightHelpers.updateThermalState(
                timestampMillis = currentTime,
                teAltitudeMeters = thermalAltitude,
                verticalSpeedMs = bruttoVario,
                isCircling = isCircling,
                isTurning = isTurning
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

        val hasWindForLevo = windEligibleForTe
        val iasBounds = sinkProvider.iasBoundsMs()
        val hasPolar = iasBounds != null

        val levoNettoResult = levoNettoCalculator.update(
            LevoNettoInput(
                wMeasMs = snapshot.baselineVario,
                iasMs = indicatedAirspeedMs,
                tasMs = trueAirspeedMs.takeIf { it.isFinite() && it > 0.1 },
                deltaTimeSeconds = request.deltaTimeSeconds,
                isFlying = request.isFlying,
                isCircling = isCircling,
                isTurning = isTurning,
                hasWind = hasWindForLevo,
                windConfidence = windConfidence,
                hasPolar = hasPolar,
                iasBounds = iasBounds
            )
        )

        val autoMcResult = autoMcCalculator.update(
            AutoMcInput(
                currentTimeMillis = currentTime,
                isCircling = isCircling,
                currentThermalLiftRate = currentThermalLift,
                currentThermalValid = currentThermalValid
            )
        )
        val mcBase = if (request.autoMcEnabled && autoMcResult.valid) {
            autoMcResult.valueMs
        } else {
            request.macCreadySetting
        }
        val mcSourceAuto = request.autoMcEnabled && autoMcResult.valid

        val stfResult = speedToFlyCalculator.update(
            SpeedToFlyInput(
                currentTimeMillis = currentTime,
                currentIasMs = indicatedAirspeedMs,
                mcBaseMs = mcBase,
                mcSourceAuto = mcSourceAuto,
                glideNettoMs = levoNettoResult.valueMs,
                glideNettoValid = levoNettoResult.valid,
                windConfidence = windConfidence,
                flightMode = request.flightMode,
                iasBounds = iasBounds
            )
        )

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
            displayNeedleVario = displayNeedleVario,
            displayNeedleVarioFast = displayNeedleVarioFast,
            displayBaselineVario = displayBaselineVario,
            displayNetto = displayNetto,
            netto = nettoResult.value.toFloat(),
            nettoValid = nettoResult.valid,
            indicatedAirspeedMs = indicatedAirspeedMs,
            trueAirspeedMs = trueAirspeedMs,
            airspeedSourceLabel = airspeedSourceLabel,
            tasValid = tasValid,
            baselineVario = snapshot.baselineVario,
            baselineVarioValid = snapshot.baselineVarioValid,
            thermalAverageCircle = thermalAvgCircle,
            thermalAverage30s = thermalAvg30s,
            thermalAverageTotal = thermalAvgTotal,
            thermalGain = thermalGain,
            thermalGainValid = thermalGainValid,
            currentThermalLiftRate = currentThermalLift,
            currentThermalValid = currentThermalValid,
            calculatedLD = calculatedLD,
            teAltitude = teAltitude,
            isCircling = isCircling,
            thermalAverage30sValid = thermalAvg30sValid,
            levoNettoMs = levoNettoResult.valueMs,
            levoNettoValid = levoNettoResult.valid,
            levoNettoHasWind = levoNettoResult.hasWind,
            levoNettoHasPolar = levoNettoResult.hasPolar,
            levoNettoConfidence = levoNettoResult.confidence,
            autoMcMs = autoMcResult.valueMs,
            autoMcValid = autoMcResult.valid,
            speedToFlyIasMs = stfResult.targetIasMs,
            speedToFlyDeltaMs = stfResult.deltaIasMs,
            speedToFlyValid = stfResult.valid,
            speedToFlyMcSourceAuto = stfResult.mcSourceAuto,
            speedToFlyHasPolar = hasPolar
        )
    }

    fun reset() {
        fusionBlackboard.resetAll()
        displaySmoother.reset()
        baselineDisplaySmoother.reset()
        needleDynamics.reset()
        fastNeedleDynamics.reset()
        circlingDetector.reset()
        sensorFrontEnd.resetDerivatives()
        levoNettoCalculator.reset()
        autoMcCalculator.reset()
        speedToFlyCalculator.reset()
        prevTeSpeed = 0.0
        groundZeroAccumulatedSeconds = 0.0
    }

    private fun altitudeForAirspeed(baroAltitude: Double, gpsAltitude: Double): Double = when {
        baroAltitude.isFinite() && baroAltitude != 0.0 -> baroAltitude
        gpsAltitude.isFinite() -> gpsAltitude
        else -> 0.0
    }

    private fun resolveExternalAirspeed(
        sample: AirspeedSample?,
        currentTimeMillis: Long
    ): AirspeedEstimate? {
        if (sample == null || !sample.valid) return null
        if (!isFreshExternalSample(sample, currentTimeMillis)) return null
        val trueMs = sample.trueMs.takeIf { it.isFinite() && it > MIN_VALID_AIRSPEED_MS } ?: return null
        val indicatedMs = sample.indicatedMs
            .takeIf { it.isFinite() && it > MIN_VALID_AIRSPEED_MS }
            ?: trueMs
        return AirspeedEstimate(
            indicatedMs = indicatedMs,
            trueMs = trueMs,
            source = AirspeedSource.EXTERNAL
        )
    }

    private fun isFreshExternalSample(sample: AirspeedSample, currentTimeMillis: Long): Boolean {
        val clockAge = sample.clockMillis.ageMillis(currentTimeMillis)
        if (clockAge != null && clockAge <= EXTERNAL_AIRSPEED_MAX_AGE_MS) {
            return true
        }
        val timestampAge = sample.timestampMillis.ageMillis(currentTimeMillis)
        return timestampAge != null && timestampAge <= EXTERNAL_AIRSPEED_MAX_AGE_MS
    }

    private fun Long.ageMillis(currentTimeMillis: Long): Long? {
        if (this <= 0L) return null
        val age = currentTimeMillis - this
        return age.takeIf { it >= 0L }
    }

    private fun smoothDisplayVario(raw: Double, deltaTime: Double, isValid: Boolean): Double =
        displaySmoother.smoothVario(raw, deltaTime, isValid)

    private fun smoothBaselineDisplayVario(raw: Double, deltaTime: Double, isValid: Boolean): Double =
        baselineDisplaySmoother.smoothVario(raw, deltaTime, isValid)

    private fun smoothDisplayNetto(raw: Double, deltaTime: Double, isValid: Boolean): Double =
        displaySmoother.smoothNetto(raw, deltaTime, isValid)

    private fun applyGroundZeroBias(
        displayVario: Double,
        groundSpeedMs: Double,
        deltaTimeSeconds: Double
    ): Double {
        if (abs(displayVario) < GROUND_ZERO_THRESHOLD_MS && groundSpeedMs < GROUND_ZERO_SPEED_MS) {
            groundZeroAccumulatedSeconds += deltaTimeSeconds
            if (groundZeroAccumulatedSeconds >= GROUND_ZERO_SETTLE_SECONDS) {
                return 0.0
            }
        } else {
            groundZeroAccumulatedSeconds = 0.0
        }
        return displayVario
    }

    companion object {
        private const val MIN_FALLBACK_GPS_SPEED_MS = 0.5
        private const val TE_MIN_SPEED_MS = 5.0
        private const val TE_MIN_DT_SECONDS = 0.05
        private const val GROUND_ZERO_THRESHOLD_MS = 0.05
        private const val GROUND_ZERO_SPEED_MS = 0.5
        private const val GROUND_ZERO_SETTLE_SECONDS = 3.0
        private const val LEVO_WIND_CONF_MIN = 0.1
        private const val MIN_VALID_AIRSPEED_MS = 0.1
        private const val EXTERNAL_AIRSPEED_MAX_AGE_MS = 3_000L
    }
}

data class FlightMetricsRequest(
    val gps: GPSData,
    val currentTimeMillis: Long,
    val wallTimeMillis: Long,
    val gpsTimestampMillis: Long,
    val deltaTimeSeconds: Double,
    val varioResult: ModernVarioResult,
    val varioGpsValue: Double,
    val baroResult: BarometricAltitudeData?,
    val windState: WindState?,
    val externalAirspeedSample: AirspeedSample? = null,
    val allowOnlineTerrainLookup: Boolean = true,
    val varioValidUntil: Long,
    val isFlying: Boolean,
    val macCreadySetting: Double,
    val autoMcEnabled: Boolean,
    val teCompensationEnabled: Boolean = true,
    val flightMode: FlightMode
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
    val displayNeedleVario: Double,
    val displayNeedleVarioFast: Double,
    val displayBaselineVario: Double,
    val displayNetto: Double,
    val netto: Float,
    val nettoValid: Boolean,
    val indicatedAirspeedMs: Double,
    val trueAirspeedMs: Double,
    val airspeedSourceLabel: String,
    val tasValid: Boolean,
    val baselineVario: Double,
    val baselineVarioValid: Boolean,
    val thermalAverageCircle: Float,
    val thermalAverage30s: Float,
    val thermalAverageTotal: Float,
    val thermalGain: Double,
    val thermalGainValid: Boolean,
    val currentThermalLiftRate: Double,
    val currentThermalValid: Boolean,
    val calculatedLD: Float,
    val teAltitude: Double,
    val isCircling: Boolean,
    val thermalAverage30sValid: Boolean,
    val levoNettoMs: Double,
    val levoNettoValid: Boolean,
    val levoNettoHasWind: Boolean,
    val levoNettoHasPolar: Boolean,
    val levoNettoConfidence: Double,
    val autoMcMs: Double,
    val autoMcValid: Boolean,
    val speedToFlyIasMs: Double,
    val speedToFlyDeltaMs: Double,
    val speedToFlyValid: Boolean,
    val speedToFlyMcSourceAuto: Boolean,
    val speedToFlyHasPolar: Boolean
)
