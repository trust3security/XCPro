package com.example.xcpro.sensors

import android.content.Context
import com.example.xcpro.audio.AudioFocusManager
import com.example.xcpro.core.time.Clock
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * DI-owned factory for creating sensor fusion pipelines with explicit scopes and data sources.
 */
class SensorFusionRepositoryFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sinkProvider: StillAirSinkProvider,
    private val windRepository: WindSensorFusionRepository,
    private val flightStateSource: FlightStateSource,
    private val audioFocusManager: AudioFocusManager,
    private val clock: Clock
) {
    fun create(
        sensorDataSource: SensorDataSource,
        scope: CoroutineScope,
        enableAudio: Boolean = true,
        isReplayMode: Boolean = false
    ): SensorFusionRepository =
        FlightDataCalculator(
            context = context,
            sensorDataSource = sensorDataSource,
            scope = scope,
            sinkProvider = sinkProvider,
            windStateFlow = windRepository.windState,
            flightStateSource = flightStateSource,
            audioFocusManager = audioFocusManager,
            clock = clock,
            enableAudio = enableAudio,
            isReplayMode = isReplayMode
        )
}
