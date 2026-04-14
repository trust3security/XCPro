package com.example.xcpro.map

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.GpsStatus
import com.example.xcpro.sensors.SensorStatus
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.sensors.domain.FlyingState
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
