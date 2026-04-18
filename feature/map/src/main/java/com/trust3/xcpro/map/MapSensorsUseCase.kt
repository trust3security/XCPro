package com.trust3.xcpro.map

import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.sensors.FlightStateSource
import com.trust3.xcpro.sensors.GpsStatus
import com.trust3.xcpro.sensors.SensorStatus
import com.trust3.xcpro.sensors.SensorFusionRepository
import com.trust3.xcpro.sensors.UnifiedSensorManager
import com.trust3.xcpro.sensors.domain.FlyingState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class MapSensorsUseCase @Inject constructor(
    private val varioRuntimeControlPort: VarioRuntimeControlPort,
    private val unifiedSensorManager: UnifiedSensorManager,
    private val flightStateSource: FlightStateSource,
    private val sensorFusionRepository: SensorFusionRepository
) {
    val gpsStatusFlow: StateFlow<GpsStatus> = unifiedSensorManager.gpsStatusFlow
    val flightStateFlow: StateFlow<FlyingState> = flightStateSource.flightState

    fun setFlightMode(mode: FlightMode) {
        sensorFusionRepository.setFlightMode(mode)
    }

    fun startSensors(): Boolean = varioRuntimeControlPort.ensureRunningIfPermitted()

    fun stopSensors() {
        varioRuntimeControlPort.requestStop()
    }

    fun sensorStatus(): SensorStatus = unifiedSensorManager.getSensorStatus()

    fun isGpsEnabled(): Boolean = unifiedSensorManager.isGpsEnabled()
}
