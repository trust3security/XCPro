package com.trust3.xcpro.replay

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.flightdata.FlightDataRepository
import com.trust3.xcpro.map.VarioRuntimeControlPort
import com.trust3.xcpro.sensors.SensorFusionRepositoryFactory
import com.trust3.xcpro.vario.LevoVarioPreferencesRepository
import com.trust3.xcpro.weather.wind.data.WindSensorFusionRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow

/**
 * DI-managed factory for replay pipelines.
 */
class ReplayPipelineFactory @Inject constructor(
    private val flightDataRepository: FlightDataRepository,
    private val varioRuntimeControlPort: VarioRuntimeControlPort,
    private val windRepository: WindSensorFusionRepository,
    private val replaySensorSource: ReplaySensorSource,
    private val sensorFusionRepositoryFactory: SensorFusionRepositoryFactory,
    private val levoVarioPreferencesRepository: LevoVarioPreferencesRepository,
    private val clock: Clock,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    fun create(sessionState: StateFlow<SessionState>, tag: String): ReplayPipeline =
        ReplayPipeline(
            flightDataRepository = flightDataRepository,
            varioRuntimeControlPort = varioRuntimeControlPort,
            windRepository = windRepository,
            replaySensorSource = replaySensorSource,
            sensorFusionRepositoryFactory = sensorFusionRepositoryFactory,
            levoVarioPreferencesRepository = levoVarioPreferencesRepository,
            clock = clock,
            dispatcher = dispatcher,
            sessionState = sessionState,
            tag = tag
        )
}
