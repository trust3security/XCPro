package com.example.xcpro.sensors

import com.example.dfcards.dfcards.calculations.TerrainElevationReadPort
import com.example.xcpro.audio.VarioAudioControllerFactory
import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.core.time.Clock
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.hawk.HawkAudioVarioReadPort
import com.example.xcpro.weather.wind.data.AirspeedDataSource
import com.example.xcpro.weather.wind.model.WindState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin wrapper around [FlightDataCalculatorEngine] to keep the public API stable.
 */
class FlightDataCalculator(
    sensorDataSource: SensorDataSource,
    airspeedDataSource: AirspeedDataSource,
    scope: CoroutineScope,
    sinkProvider: StillAirSinkProvider,
    windStateFlow: StateFlow<WindState>,
    flightStateSource: FlightStateSource,
    audioControllerFactory: VarioAudioControllerFactory,
    clock: Clock,
    hawkAudioVarioReadPort: HawkAudioVarioReadPort,
    terrainElevationReadPort: TerrainElevationReadPort,
    enableAudio: Boolean = true,
    isReplayMode: Boolean = false
) : SensorFusionRepository {

    private val engine = FlightDataCalculatorEngine(
        sensorDataSource = sensorDataSource,
        airspeedDataSource = airspeedDataSource,
        scope = scope,
        sinkProvider = sinkProvider,
        windStateFlow = windStateFlow,
        flightStateSource = flightStateSource,
        audioController = audioControllerFactory.create(scope = scope, enableAudio = enableAudio),
        clock = clock,
        hawkAudioVarioReadPort = hawkAudioVarioReadPort,
        terrainElevationReadPort = terrainElevationReadPort,
        isReplayMode = isReplayMode
    )

    override val flightDataFlow: StateFlow<CompleteFlightData?> = engine.flightDataFlow
    override val diagnosticsFlow: StateFlow<VarioDiagnosticsSample?> = engine.diagnosticsFlow
    override val audioSettings: StateFlow<VarioAudioSettings> = engine.audioSettings

    override fun updateAudioSettings(settings: VarioAudioSettings) = engine.updateAudioSettings(settings)
    override fun setHawkAudioEnabled(enabled: Boolean) = engine.setHawkAudioEnabled(enabled)

    override fun setManualQnh(qnhHPa: Double) = engine.setManualQnh(qnhHPa)

    override fun resetQnhToStandard() = engine.resetQnhToStandard()

    override fun setMacCreadySetting(value: Double) = engine.setMacCreadySetting(value)

    override fun setMacCreadyRisk(value: Double) = engine.setMacCreadyRisk(value)

    override fun setAutoMcEnabled(enabled: Boolean) = engine.setAutoMcEnabled(enabled)

    override fun setTotalEnergyCompensationEnabled(enabled: Boolean) =
        engine.setTotalEnergyCompensationEnabled(enabled)

    override fun setFlightMode(mode: com.example.xcpro.common.flight.FlightMode) = engine.setFlightMode(mode)

    override fun updateReplayRealVario(realVarioMs: Double?, timestampMillis: Long) =
        engine.updateReplayRealVario(realVarioMs, timestampMillis)

    override fun stop() = engine.stop()
}
