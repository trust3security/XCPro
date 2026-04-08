package com.example.xcpro.sensors

import com.example.xcpro.core.flight.calculations.TerrainElevationReadPort
import com.example.xcpro.audio.VarioAudioControllerFactory
import com.example.xcpro.core.time.Clock
import com.example.xcpro.di.LiveSource
import com.example.xcpro.di.ReplaySource
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.hawk.HawkAudioVarioReadPort
import com.example.xcpro.weather.wind.data.AirspeedDataSource
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * DI-owned factory for creating sensor fusion pipelines with explicit scopes and data sources.
 */
class SensorFusionRepositoryFactory @Inject constructor(
    private val sinkProvider: StillAirSinkProvider,
    private val windRepository: WindSensorFusionRepository,
    private val flightStateSource: FlightStateSource,
    @LiveSource private val liveAirspeedSource: AirspeedDataSource,
    @ReplaySource private val replayAirspeedSource: AirspeedDataSource,
    private val audioControllerFactory: VarioAudioControllerFactory,
    private val clock: Clock,
    private val hawkAudioVarioReadPort: HawkAudioVarioReadPort,
    private val terrainElevationReadPort: TerrainElevationReadPort
) {
    fun create(
        sensorDataSource: SensorDataSource,
        scope: CoroutineScope,
        enableAudio: Boolean = true,
        isReplayMode: Boolean = false
    ): SensorFusionRepository {
        val airspeedSource = if (isReplayMode) replayAirspeedSource else liveAirspeedSource
        return FlightDataCalculator(
            sensorDataSource = sensorDataSource,
            airspeedDataSource = airspeedSource,
            scope = scope,
            sinkProvider = sinkProvider,
            windStateFlow = windRepository.windState,
            flightStateSource = flightStateSource,
            audioControllerFactory = audioControllerFactory,
            clock = clock,
            hawkAudioVarioReadPort = hawkAudioVarioReadPort,
            terrainElevationReadPort = terrainElevationReadPort,
            enableAudio = enableAudio,
            isReplayMode = isReplayMode
        )
    }
}
