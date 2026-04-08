package com.example.xcpro.map

import com.example.xcpro.sensors.GpsStatus
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.vario.VarioServiceManager
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
            varioServiceManager = buildManager()
        )

        assertTrue(useCase.startSensors())
        verify(runtimeControlPort).ensureRunningIfPermitted()
    }

    @Test
    fun `stopSensors delegates to runtime control port`() {
        val runtimeControlPort = mock<VarioRuntimeControlPort>()

        val useCase = MapSensorsUseCase(
            varioRuntimeControlPort = runtimeControlPort,
            varioServiceManager = buildManager()
        )

        useCase.stopSensors()

        verify(runtimeControlPort).requestStop()
    }

    private fun buildManager(): VarioServiceManager {
        val manager = mock<VarioServiceManager>()
        val sensorManager = mock<UnifiedSensorManager>()
        val flightStateSource = object : FlightStateSource {
            override val flightState = MutableStateFlow(FlyingState())
        }
        whenever(manager.unifiedSensorManager).thenReturn(sensorManager)
        whenever(manager.flightStateSource).thenReturn(flightStateSource)
        whenever(sensorManager.gpsStatusFlow).thenReturn(MutableStateFlow<GpsStatus>(GpsStatus.Searching))
        return manager
    }
}
