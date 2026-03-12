package com.example.xcpro.map

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.sensors.GpsStatus
import com.example.xcpro.sensors.SensorStatus
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.vario.VarioServiceManager
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class MapSensorsUseCase @Inject constructor(
    private val varioServiceManager: VarioServiceManager
) {
    val gpsStatusFlow: StateFlow<GpsStatus> = varioServiceManager.unifiedSensorManager.gpsStatusFlow
    val flightStateFlow: StateFlow<FlyingState> = varioServiceManager.flightStateSource.flightState

    fun setFlightMode(mode: FlightMode) {
        varioServiceManager.setFlightMode(mode)
    }

    suspend fun startSensors(): Boolean = varioServiceManager.start()

    fun stopSensors() {
        varioServiceManager.stop()
    }

    fun sensorStatus(): SensorStatus = varioServiceManager.unifiedSensorManager.getSensorStatus()

    fun isGpsEnabled(): Boolean = varioServiceManager.unifiedSensorManager.isGpsEnabled()
}
