package com.example.xcpro.map

import com.example.xcpro.sensors.SensorStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LocationSensorsControllerTest {

    @Test
    fun `accepted start request does not require immediate gps readiness`() = runTest {
        val sensorsUseCase = mock<MapSensorsUseCase>()
        whenever(sensorsUseCase.sensorStatus()).thenReturn(
            sensorStatus(hasLocationPermissions = true)
        )
        whenever(sensorsUseCase.startSensors()).thenReturn(true)

        val controller = LocationSensorsController(
            context = mock(),
            scope = backgroundScope,
            sensorsUseCase = sensorsUseCase
        )

        controller.onLocationPermissionsResult(fineLocationGranted = true)
        runCurrent()
        controller.stopLocationTracking(force = true)

        verify(sensorsUseCase).startSensors()
        verify(sensorsUseCase).stopSensors()
    }

    @Test
    fun `restartSensorsIfNeeded reissues request when sensors still report stopped`() = runTest {
        val sensorsUseCase = mock<MapSensorsUseCase>()
        whenever(sensorsUseCase.sensorStatus()).thenReturn(
            sensorStatus(hasLocationPermissions = true)
        )
        whenever(sensorsUseCase.startSensors()).thenReturn(true)

        val controller = LocationSensorsController(
            context = mock(),
            scope = backgroundScope,
            sensorsUseCase = sensorsUseCase
        )

        controller.restartSensorsIfNeeded()
        advanceTimeBy(100)
        runCurrent()
        controller.restartSensorsIfNeeded()
        advanceTimeBy(100)
        runCurrent()

        verify(sensorsUseCase, times(2)).startSensors()
        verify(sensorsUseCase).stopSensors()
    }

    @Test
    fun `force stop requests stop when non-gps runtime sensors are still active`() = runTest {
        val sensorsUseCase = mock<MapSensorsUseCase>()
        whenever(sensorsUseCase.sensorStatus()).thenReturn(
            sensorStatus(hasLocationPermissions = true, baroStarted = true)
        )

        val controller = LocationSensorsController(
            context = mock(),
            scope = backgroundScope,
            sensorsUseCase = sensorsUseCase
        )

        controller.stopLocationTracking(force = true)

        verify(sensorsUseCase).stopSensors()
    }

    private fun sensorStatus(
        hasLocationPermissions: Boolean,
        gpsStarted: Boolean = false,
        baroStarted: Boolean = false,
        compassStarted: Boolean = false,
        accelStarted: Boolean = false,
        rotationStarted: Boolean = false
    ): SensorStatus {
        return SensorStatus(
            gpsAvailable = true,
            gpsStarted = gpsStarted,
            baroAvailable = true,
            baroStarted = baroStarted,
            compassAvailable = true,
            compassStarted = compassStarted,
            accelAvailable = true,
            accelStarted = accelStarted,
            rotationAvailable = true,
            rotationStarted = rotationStarted,
            hasLocationPermissions = hasLocationPermissions
        )
    }
}
