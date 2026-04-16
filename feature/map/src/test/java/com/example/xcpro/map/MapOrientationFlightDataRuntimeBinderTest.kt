package com.example.xcpro.map

import com.example.xcpro.common.orientation.OrientationController
import com.example.xcpro.core.flight.RealTimeFlightData
import com.example.xcpro.toOrientationFlightDataSnapshot
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class MapOrientationFlightDataRuntimeBinderTest {

    @Test
    fun applyFlightData_forwardsNonNullSamplesToOrientationRuntime() {
        val orientationController: OrientationController = mock()
        val binder = MapOrientationFlightDataRuntimeBinder(
            flightDataManager = mock(),
            orientationController = orientationController
        )
        val sample = RealTimeFlightData(
            latitude = -37.8,
            longitude = 145.0,
            track = 123.0,
            groundSpeed = 42.0,
            accuracy = 6.0,
            windDirection = 181f,
            windSpeed = 18f,
            windValid = true
        )

        binder.applyFlightData(sample)

        verify(orientationController).updateFromFlightData(eq(sample.toOrientationFlightDataSnapshot()))
    }

    @Test
    fun applyFlightData_nullSample_isIgnored() {
        val orientationController: OrientationController = mock()
        val binder = MapOrientationFlightDataRuntimeBinder(
            flightDataManager = mock(),
            orientationController = orientationController
        )

        binder.applyFlightData(null)

        verifyNoInteractions(orientationController)
    }
}
