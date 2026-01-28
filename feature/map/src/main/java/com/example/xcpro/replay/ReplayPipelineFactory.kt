package com.example.xcpro.replay

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.core.time.Clock
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.SensorFusionRepositoryFactory
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow

/**
 * DI-managed factory for replay pipelines.
 */
class ReplayPipelineFactory @Inject constructor(
    private val flightDataRepository: FlightDataRepository,
    private val varioServiceManager: VarioServiceManager,
    private val windRepository: WindSensorFusionRepository,
    private val replaySensorSource: ReplaySensorSource,
    private val sensorFusionRepositoryFactory: SensorFusionRepositoryFactory,
    private val clock: Clock,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    fun create(sessionState: StateFlow<SessionState>, tag: String): ReplayPipeline =
        ReplayPipeline(
            flightDataRepository = flightDataRepository,
            varioServiceManager = varioServiceManager,
            windRepository = windRepository,
            replaySensorSource = replaySensorSource,
            sensorFusionRepositoryFactory = sensorFusionRepositoryFactory,
            clock = clock,
            dispatcher = dispatcher,
            sessionState = sessionState,
            tag = tag
        )
}
