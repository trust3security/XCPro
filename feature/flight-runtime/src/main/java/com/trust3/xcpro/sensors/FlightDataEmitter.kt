package com.trust3.xcpro.sensors

import com.trust3.xcpro.core.flight.calculations.BarometricAltitudeData
import com.trust3.xcpro.core.flight.filters.ModernVarioResult
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.external.ExternalFlightSettingsSnapshot
import com.trust3.xcpro.external.ExternalInstrumentFlightSnapshot
import com.trust3.xcpro.flightdata.FlightDisplayMapper
import com.trust3.xcpro.flightdata.FlightDisplaySnapshot
import com.trust3.xcpro.sensors.domain.CalculateFlightMetricsUseCase
import com.trust3.xcpro.sensors.domain.FlightMetricsRequest
import com.trust3.xcpro.weather.wind.model.AirspeedSample
import com.trust3.xcpro.weather.wind.model.WindState
import com.trust3.xcpro.common.flight.FlightMode
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/**
 * Builds display-ready flight frames from fused sensor inputs.
 *
 * AI-NOTE: Keeps presentation mapping and logging out of the hot sensor loops for clarity.
 */
internal class FlightDataEmitter(
    private val state: FlightDataEmissionState,
    private val flightMetricsUseCase: CalculateFlightMetricsUseCase,
    private val flightDisplayMapper: FlightDisplayMapper,
    private val flightHelpers: FlightCalculationHelpers,
    private val varioSuite: VarioSuite,
    private val isReplayMode: Boolean,
    private val flightDataFlow: MutableStateFlow<CompleteFlightData?>,
    private val logThermalMetrics: Boolean,
    private val tag: String
) {

    fun emit(
        gps: GPSData,
        compass: CompassData?,
        currentTime: Long,
        wallTimeMillis: Long,
        outputTimestampMillis: Long,
        deltaTime: Double,
        varioResultInput: ModernVarioResult,
        baroResult: BarometricAltitudeData?,
        baro: BaroData?,
        cachedVarioResult: ModernVarioResult?,
        windState: WindState?,
        externalAirspeedSample: AirspeedSample?,
        externalInstrumentSnapshot: ExternalInstrumentFlightSnapshot,
        externalFlightSettingsSnapshot: ExternalFlightSettingsSnapshot,
        isFlying: Boolean,
        replayRealVarioMs: Double?,
        replayRealVarioTimestamp: Long,
        macCreadySetting: Double,
        macCreadyRisk: Double,
        autoMcEnabled: Boolean,
        teCompensationEnabled: Boolean,
        flightMode: FlightMode
    ) {
        val replayIgcVario = if (isReplayMode && replayRealVarioTimestamp != 0L) {
            val ageMs = currentTime - replayRealVarioTimestamp
            if (ageMs in 0..FlightDataConstants.REPLAY_VARIO_MAX_AGE_MS) replayRealVarioMs else null
        } else {
            null
        }

        val gpsVarioValue = varioSuite.gpsVerticalSpeed().takeIf { it.isFinite() } ?: 0.0
        val varioResultForMetrics = if (isReplayMode && replayIgcVario != null) {
            varioResultInput.copy(verticalSpeed = replayIgcVario)
        } else {
            varioResultInput
        }
        val varioGpsValueForMetrics = if (isReplayMode && replayIgcVario != null) {
            replayIgcVario
        } else {
            gpsVarioValue
        }

        val metrics = flightMetricsUseCase.execute(
            FlightMetricsRequest(
                gps = gps,
                currentTimeMillis = currentTime,
                wallTimeMillis = wallTimeMillis,
                gpsTimestampMillis = gps.timeForCalculationsMillis,
                deltaTimeSeconds = deltaTime,
                varioResult = varioResultForMetrics,
                varioGpsValue = varioGpsValueForMetrics,
                baroResult = baroResult,
                windState = windState,
                externalAirspeedSample = externalAirspeedSample,
                externalInstrumentSnapshot = externalInstrumentSnapshot,
                allowOnlineTerrainLookup = !isReplayMode,
                varioValidUntil = state.varioValidUntil,
                isFlying = isFlying,
                macCreadySetting = macCreadySetting,
                autoMcEnabled = autoMcEnabled,
                teCompensationEnabled = teCompensationEnabled,
                flightMode = flightMode,
                isReplayMode = isReplayMode
            )
        )

        state.latestTeVario = metrics.teVario

        val varioResults = varioSuite.verticalSpeeds()

        val dataQuality = buildString {
            append("GPS")
            if (baro != null) append("+BARO")
            if (compass != null) append("+COMPASS")
            if (cachedVarioResult != null) append("+IMU")
            append("+VARIO:")
            append(metrics.varioSource)
            if (flightHelpers.currentAGL.isFinite()) append("+AGL")
            append("+50Hz")
        }

        val snapshot = FlightDisplaySnapshot(
            gps = gps,
            baro = baro,
            compass = compass,
            metrics = metrics,
            aglMeters = flightHelpers.currentAGL,
            aglUpdatedAtMonoMs = flightHelpers.lastSuccessfulAglUpdateMonoMs,
            varioResults = varioResults,
            replayIgcVario = replayIgcVario,
            audioVario = state.latestAudioVario,
            dataQuality = dataQuality,
            // Wall time for live UI, IGC time for replay UI.
            timestamp = outputTimestampMillis,
            macCready = macCreadySetting,
            macCreadyRisk = macCreadyRisk,
            externalMacCreadyActive = externalFlightSettingsSnapshot.macCreadyMps != null,
            externalQnhActive = externalFlightSettingsSnapshot.qnhHpa != null,
            bugsPercent = externalFlightSettingsSnapshot.bugsPercent,
            ballastOverloadFactor = externalFlightSettingsSnapshot.ballastOverloadFactor,
            outsideAirTemperatureC = externalFlightSettingsSnapshot.outsideAirTemperatureC
        )
        val flightData = flightDisplayMapper.map(snapshot)

        if (isReplayMode && wallTimeMillis % 1_000L < 50L) {
            AppLogger.d(
                tag,
                "REPLAY_CHOICE replayIgc=${replayIgcVario ?: Double.NaN} " +
                    "pressureVario=${varioResultInput.verticalSpeed} " +
                    "chosen=${metrics.verticalSpeed} " +
                    "display=${flightData.displayVario.value} " +
                    "valid=${metrics.varioValid} src=${metrics.varioSource}"
            )
        }

        if (logThermalMetrics && currentTime - state.lastThermalLogTime >= 1000L) {
            AppLogger.d(
                tag,
                "Thermal metrics: TC30=${flightData.thermalAverage.value} " +
                    "TC_AVG=${flightData.thermalAverageCircle.value} " +
                    "T_AVG=${flightData.thermalAverageTotal.value} " +
                    "TC_GAIN=${flightData.thermalGain.value}"
            )
            state.lastThermalLogTime = currentTime
        }

        flightDataFlow.value = flightData
        state.lastUpdateTime = currentTime

        if (currentTime % 1000 < 100) {
            val gpsVarioMs = metrics.verticalSpeed.takeIf { metrics.varioSource == "GPS" }
            val pressureVarioMs = metrics.verticalSpeed.takeIf { metrics.varioSource == "PRESSURE" }
            val aglLabel = flightHelpers.currentAGL.takeIf { it.isFinite() }?.toInt()?.toString() ?: "NA"
            AppLogger.d(
                tag,
                "[SLOW] " +
                    (if (cachedVarioResult != null) "PRIORITY2-50Hz(IMU+BARO)" else "PRIORITY2-50Hz(BARO)") + " " +
                    "GPSalt=${gps.altitude.value.toInt()}m BaroAlt=${metrics.baroAltitude.toInt()}m " +
                    "RawBaro=${String.format(Locale.US, "%.2f", varioResultInput.verticalSpeed)} " +
                    "Levo=${String.format(Locale.US, "%.2f", metrics.verticalSpeed)}(src=${metrics.varioSource},val=${metrics.varioValid}) " +
                    "BASE=${String.format(Locale.US, "%.2f", metrics.baselineVario)}(val=${metrics.baselineVarioValid}) " +
                    "GPSv=${gpsVarioMs?.let { String.format(Locale.US, "%.2f", it) } ?: "--"} " +
                    "PressV=${pressureVarioMs?.let { String.format(Locale.US, "%.2f", it) } ?: "--"} " +
                    "Spd=${String.format(Locale.US, "%.1f", gps.speed.value)} " +
                    "AGL=$aglLabel " +
                    "QNH=${String.format(Locale.US, "%.1f", baroResult?.qnh ?: Double.NaN)} " +
                    "cal=${baroResult?.isCalibrated == true}"
            )
        }
    }
}

internal class FlightDataEmissionState {
    @Volatile var lastUpdateTime: Long = 0L
    @Volatile var lastThermalLogTime: Long = 0L
    @Volatile var latestTeVario: Double? = null
    @Volatile var latestAudioVario: Double = 0.0
    @Volatile var varioValidUntil: Long = 0L

    fun reset() {
        lastUpdateTime = 0L
        lastThermalLogTime = 0L
        latestTeVario = null
        latestAudioVario = 0.0
        varioValidUntil = 0L
    }
}
