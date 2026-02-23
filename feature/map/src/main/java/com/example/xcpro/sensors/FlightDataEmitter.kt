package com.example.xcpro.sensors

import android.util.Log
import com.example.dfcards.calculations.BarometricAltitudeData
import com.example.dfcards.filters.ModernVarioResult
import com.example.xcpro.flightdata.FlightDisplayMapper
import com.example.xcpro.flightdata.FlightDisplaySnapshot
import com.example.xcpro.sensors.domain.CalculateFlightMetricsUseCase
import com.example.xcpro.sensors.domain.FlightMetricsRequest
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.common.flight.FlightMode
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
                varioValidUntil = state.varioValidUntil,
                isFlying = isFlying,
                macCreadySetting = macCreadySetting,
                autoMcEnabled = autoMcEnabled,
                teCompensationEnabled = teCompensationEnabled,
                flightMode = flightMode
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
            if (flightHelpers.currentAGL > 0) append("+AGL")
            append("+50Hz")
        }

        val snapshot = FlightDisplaySnapshot(
            gps = gps,
            baro = baro,
            compass = compass,
            metrics = metrics,
            aglMeters = flightHelpers.currentAGL,
            varioResults = varioResults,
            replayIgcVario = replayIgcVario,
            audioVario = state.latestAudioVario,
            dataQuality = dataQuality,
            // Wall time for live UI, IGC time for replay UI.
            timestamp = outputTimestampMillis,
            macCready = macCreadySetting,
            macCreadyRisk = macCreadyRisk
        )
        val flightData = flightDisplayMapper.map(snapshot)

        if (isReplayMode && wallTimeMillis % 1_000L < 50L) {
            Log.d(
                tag,
                "REPLAY_CHOICE replayIgc=${replayIgcVario ?: Double.NaN} " +
                    "pressureVario=${varioResultInput.verticalSpeed} " +
                    "chosen=${metrics.verticalSpeed} " +
                    "display=${flightData.displayVario.value} " +
                    "valid=${metrics.varioValid} src=${metrics.varioSource}"
            )
        }

        if (logThermalMetrics && currentTime - state.lastThermalLogTime >= 1000L) {
            Log.d(
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
            Log.d(
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
                    "AGL=${flightHelpers.currentAGL.toInt()} " +
                    "QNH=${String.format(Locale.US, "%.1f", baroResult?.qnh ?: Double.NaN)} " +
                    "cal=${baroResult?.isCalibrated == true}"
            )
        }
    }
}

internal class FlightDataEmissionState {
    var lastUpdateTime: Long = 0L
    var lastThermalLogTime: Long = 0L
    var latestTeVario: Double? = null
    var latestAudioVario: Double = 0.0
    var varioValidUntil: Long = 0L

    fun reset() {
        lastUpdateTime = 0L
        lastThermalLogTime = 0L
        latestTeVario = null
        latestAudioVario = 0.0
        varioValidUntil = 0L
    }
}
