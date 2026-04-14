package com.example.xcpro.map

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.GpsStatus
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.SensorStatus
import com.example.xcpro.sensors.UnifiedSensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MapSensorsUseCaseTest {

    @Test
    fun `startSensors delegates to runtime control port`() {
        val runtimeControlPort = mock<VarioRuntimeControlPort>()
        whenever(runtimeControlPort.ensureRunningIfPermitted()).thenReturn(true)

        val useCase = MapSensorsUseCase(
            varioRuntimeControlPort = runtimeControlPort,
            unifiedSensorManager = buildSensorManager(),
            flightStateSource = buildFlightStateSource(),
            sensorFusionRepository = mock()
        )

        assertTrue(useCase.startSensors())
        verify(runtimeControlPort).ensureRunningIfPermitted()
    }

    @Test
    fun `stopSensors delegates to runtime control port`() {
        val runtimeControlPort = mock<VarioRuntimeControlPort>()

        val useCase = MapSensorsUseCase(
            varioRuntimeControlPort = runtimeControlPort,
            unifiedSensorManager = buildSensorManager(),
            flightStateSource = buildFlightStateSource(),
            sensorFusionRepository = mock()
        )

        useCase.stopSensors()

        verify(runtimeControlPort).requestStop()
    }

    @Test
    fun `setFlightMode delegates to sensor fusion repository`() {
        val sensorFusionRepository = mock<SensorFusionRepository>()

        val useCase = MapSensorsUseCase(
            varioRuntimeControlPort = mock(),
            unifiedSensorManager = buildSensorManager(),
            flightStateSource = buildFlightStateSource(),
            sensorFusionRepository = sensorFusionRepository
        )

        useCase.setFlightMode(FlightMode.THERMAL)

        verify(sensorFusionRepository).setFlightMode(FlightMode.THERMAL)
    }

    @Test
    fun `sensorStatus delegates to unified sensor manager`() {
        val sensorManager = buildSensorManager()
        val expected = SensorStatus(
            gpsAvailable = true,
            gpsStarted = true,
            baroAvailable = true,
            baroStarted = true,
            compassAvailable = false,
            compassStarted = false,
            accelAvailable = false,
            accelStarted = false,
            rotationAvailable = false,
            rotationStarted = false,
            hasLocationPermissions = true
        )
        whenever(sensorManager.getSensorStatus()).thenReturn(expected)

        val useCase = MapSensorsUseCase(
            varioRuntimeControlPort = mock(),
            unifiedSensorManager = sensorManager,
            flightStateSource = buildFlightStateSource(),
            sensorFusionRepository = mock()
        )

        assertTrue(useCase.sensorStatus() == expected)
        verify(sensorManager).getSensorStatus()
    }

    @Test
    fun `isGpsEnabled delegates to unified sensor manager`() {
        val sensorManager = buildSensorManager()
        whenever(sensorManager.isGpsEnabled()).thenReturn(true)

        val useCase = MapSensorsUseCase(
            varioRuntimeControlPort = mock(),
            unifiedSensorManager = sensorManager,
            flightStateSource = buildFlightStateSource(),
            sensorFusionRepository = mock()
        )

        assertTrue(useCase.isGpsEnabled())
        verify(sensorManager).isGpsEnabled()
    }

    private fun buildSensorManager(): UnifiedSensorManager {
        val sensorManager = mock<UnifiedSensorManager>()
        whenever(sensorManager.gpsStatusFlow).thenReturn(MutableStateFlow<GpsStatus>(GpsStatus.Searching))
        return sensorManager
    }

    private fun buildFlightStateSource(): FlightStateSource {
        return object : FlightStateSource {
            override val flightState = MutableStateFlow(com.example.xcpro.sensors.domain.FlyingState())
        }
    }
}
