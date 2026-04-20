package com.trust3.xcpro.map

import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.livesource.LiveSourceStatePort
import com.trust3.xcpro.livesource.LiveSourceStatus
import com.trust3.xcpro.livesource.ResolvedLiveSourceState
import com.trust3.xcpro.sensors.FlightStateSource
import com.trust3.xcpro.sensors.SensorFusionRepository
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
            liveSourceStatePort = buildLiveSourceStatePort(),
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
            liveSourceStatePort = buildLiveSourceStatePort(),
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
            liveSourceStatePort = buildLiveSourceStatePort(),
            flightStateSource = buildFlightStateSource(),
            sensorFusionRepository = sensorFusionRepository
        )

        useCase.setFlightMode(FlightMode.THERMAL)

        verify(sensorFusionRepository).setFlightMode(FlightMode.THERMAL)
    }

    private fun buildFlightStateSource(): FlightStateSource {
        return object : FlightStateSource {
            override val flightState = MutableStateFlow(com.trust3.xcpro.sensors.domain.FlyingState())
        }
    }

    private fun buildLiveSourceStatePort(): LiveSourceStatePort {
        return object : LiveSourceStatePort {
            override val state = MutableStateFlow(
                ResolvedLiveSourceState(status = LiveSourceStatus.PhoneReady)
            )

            override fun refreshAndGetState(): ResolvedLiveSourceState = state.value
        }
    }
}
