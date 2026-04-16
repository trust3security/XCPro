package com.example.xcpro.map

import com.example.xcpro.common.orientation.OrientationFlightDataSnapshot
import com.example.xcpro.core.flight.RealTimeFlightData
import com.example.xcpro.toOrientationFlightDataSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@OptIn(ExperimentalCoroutinesApi::class)
class MapOrientationFlightDataRuntimeBinderTest {

    @Test
    fun applyFlightData_forwardsNonNullSamplesToOrientationRuntime() {
        val orientationRuntimePort: MapOrientationFlightDataRuntimePort = mock()
        val binder = MapOrientationFlightDataRuntimeBinder(
            liveFlightDataFlow = mock(),
            orientationRuntimePort = orientationRuntimePort
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

        verify(orientationRuntimePort).updateFromFlightData(eq(sample.toOrientationFlightDataSnapshot()))
    }

    @Test
    fun applyFlightData_nullSample_isIgnored() {
        val orientationRuntimePort: MapOrientationFlightDataRuntimePort = mock()
        val binder = MapOrientationFlightDataRuntimeBinder(
            liveFlightDataFlow = mock(),
            orientationRuntimePort = orientationRuntimePort
        )

        binder.applyFlightData(null)

        verifyNoInteractions(orientationRuntimePort)
    }

    @Test
    fun collectLatestFlightData_forwardsFlowEmissionsThroughOrientationPort() = runTest {
        val forwardedSnapshots = mutableListOf<OrientationFlightDataSnapshot>()
        val liveFlightDataFlow = MutableStateFlow<RealTimeFlightData?>(null)
        val binder = MapOrientationFlightDataRuntimeBinder(
            liveFlightDataFlow = liveFlightDataFlow,
            orientationRuntimePort = object : MapOrientationFlightDataRuntimePort {
                override fun updateFromFlightData(snapshot: OrientationFlightDataSnapshot) {
                    forwardedSnapshots += snapshot
                }
            }
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

        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            binder.collectLatestFlightData()
        }
        advanceUntilIdle()
        assertEquals(emptyList<OrientationFlightDataSnapshot>(), forwardedSnapshots)

        liveFlightDataFlow.value = sample
        advanceUntilIdle()

        assertEquals(listOf(sample.toOrientationFlightDataSnapshot()), forwardedSnapshots)
        collectionJob.cancel()
    }
}
