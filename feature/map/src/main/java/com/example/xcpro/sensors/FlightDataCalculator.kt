package com.example.xcpro.sensors

import android.content.Context
import com.example.xcpro.audio.AudioFocusManager
import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.core.time.Clock
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.weather.wind.model.WindState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin wrapper around [FlightDataCalculatorEngine] to keep the public API stable.
 */
class FlightDataCalculator(
    context: Context,
    sensorDataSource: SensorDataSource,
    scope: CoroutineScope,
    sinkProvider: StillAirSinkProvider,
    windStateFlow: StateFlow<WindState>,
    flightStateSource: FlightStateSource,
    audioFocusManager: AudioFocusManager,
    clock: Clock,
    enableAudio: Boolean = true,
    isReplayMode: Boolean = false
) : SensorFusionRepository {

    private val engine = FlightDataCalculatorEngine(
        context = context,
        sensorDataSource = sensorDataSource,
        scope = scope,
        sinkProvider = sinkProvider,
        windStateFlow = windStateFlow,
        flightStateSource = flightStateSource,
        audioFocusManager = audioFocusManager,
        clock = clock,
        enableAudio = enableAudio,
        isReplayMode = isReplayMode
    )

    val audioEngine get() = engine.audioEngine

    override val flightDataFlow: StateFlow<CompleteFlightData?> = engine.flightDataFlow
    override val diagnosticsFlow: StateFlow<VarioDiagnosticsSample?> = engine.diagnosticsFlow
    override val audioSettings: StateFlow<VarioAudioSettings> = engine.audioSettings

    override fun updateAudioSettings(settings: VarioAudioSettings) = engine.updateAudioSettings(settings)

    override fun setManualQnh(qnhHPa: Double) = engine.setManualQnh(qnhHPa)

    override fun resetQnhToStandard() = engine.resetQnhToStandard()

    override fun setMacCreadySetting(value: Double) = engine.setMacCreadySetting(value)

    override fun setMacCreadyRisk(value: Double) = engine.setMacCreadyRisk(value)

    override fun setAutoMcEnabled(enabled: Boolean) = engine.setAutoMcEnabled(enabled)

    override fun setFlightMode(mode: com.example.xcpro.common.flight.FlightMode) = engine.setFlightMode(mode)

    override fun updateReplayRealVario(realVarioMs: Double?, timestampMillis: Long) =
        engine.updateReplayRealVario(realVarioMs, timestampMillis)

    override fun stop() = engine.stop()
}
