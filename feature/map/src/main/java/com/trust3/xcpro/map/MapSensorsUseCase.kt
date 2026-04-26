package com.trust3.xcpro.map

import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.livesource.LiveSourceStatePort
import com.trust3.xcpro.livesource.LiveSourceStatus
import com.trust3.xcpro.sensors.FlightStateSource
import com.trust3.xcpro.sensors.SensorFusionRepository
import com.trust3.xcpro.sensors.domain.FlyingState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MapSensorsUseCase @Inject constructor(
    private val varioRuntimeControlPort: VarioRuntimeControlPort,
    liveSourceStatePort: LiveSourceStatePort,
    private val flightStateSource: FlightStateSource,
    private val sensorFusionRepository: SensorFusionRepository
) {
    val gpsStatusFlow: Flow<LiveSourceStatus> = liveSourceStatePort.state
        .map { it.status }
        .distinctUntilChanged()
    val flightStateFlow: StateFlow<FlyingState> = flightStateSource.flightState

    fun setFlightMode(mode: FlightMode) {
        sensorFusionRepository.setFlightMode(mode)
    }

    fun startSensors(): Boolean = varioRuntimeControlPort.ensureRunningIfPermitted()

    fun stopSensors() {
        varioRuntimeControlPort.requestStop()
    }
}
