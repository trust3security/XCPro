package com.trust3.xcpro.sensors

import com.trust3.xcpro.core.flight.calculations.TerrainElevationReadPort
import com.trust3.xcpro.audio.VarioAudioControllerFactory
import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.di.LiveSource
import com.trust3.xcpro.di.ReplaySource
import com.trust3.xcpro.external.ExternalFlightSettingsReadPort
import com.trust3.xcpro.external.ExternalInstrumentReadPort
import com.trust3.xcpro.glider.StillAirSinkProvider
import com.trust3.xcpro.hawk.HawkAudioVarioReadPort
import com.trust3.xcpro.weather.wind.data.AirspeedDataSource
import com.trust3.xcpro.weather.wind.data.WindSensorFusionRepository
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
    private val externalInstrumentReadPort: ExternalInstrumentReadPort,
    private val externalFlightSettingsReadPort: ExternalFlightSettingsReadPort,
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
            externalInstrumentReadPort = externalInstrumentReadPort,
            externalFlightSettingsReadPort = externalFlightSettingsReadPort,
            terrainElevationReadPort = terrainElevationReadPort,
            enableAudio = enableAudio,
            isReplayMode = isReplayMode
        )
    }
}
