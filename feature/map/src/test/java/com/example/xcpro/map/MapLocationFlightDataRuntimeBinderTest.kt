package com.example.xcpro.map

import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.core.flight.RealTimeFlightData
import org.junit.Test
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class MapLocationFlightDataRuntimeBinderTest {

    @Test
    fun applyFlightData_forwardsNonNullSamplesWhenReplayLocationUpdatesAreEnabled() {
        val locationManager: MapLocationRuntimePort = mock()
        val orientation = OrientationData(bearing = 123.0, timestamp = 5L)
        val binder = MapLocationFlightDataRuntimeBinder(
            liveFlightDataFlow = mock(),
            locationManager = locationManager,
            orientationProvider = { orientation },
            shouldForwardReplayLocationUpdate = { true }
        )
        val sample = sample(timestamp = 10L)

        binder.applyFlightData(sample)

        verify(locationManager).updateLocationFromReplayFrame(
            eq(sample.toReplayLocationFrame()),
            eq(orientation)
        )
    }

    @Test
    fun applyFlightData_nullSample_isIgnored() {
        val locationManager: MapLocationRuntimePort = mock()
        val binder = MapLocationFlightDataRuntimeBinder(
            liveFlightDataFlow = mock(),
            locationManager = locationManager,
            orientationProvider = { OrientationData() },
            shouldForwardReplayLocationUpdate = { true }
        )

        binder.applyFlightData(null)

        verifyNoInteractions(locationManager)
    }

    @Test
    fun applyFlightData_gateClosed_isIgnored() {
        val locationManager: MapLocationRuntimePort = mock()
        val binder = MapLocationFlightDataRuntimeBinder(
            liveFlightDataFlow = mock(),
            locationManager = locationManager,
            orientationProvider = { OrientationData() },
            shouldForwardReplayLocationUpdate = { false }
        )

        binder.applyFlightData(sample(timestamp = 11L))

        verifyNoInteractions(locationManager)
    }

    @Test
    fun applyFlightData_readsLatestGatePerEmissionWithoutRecreatingBinder() {
        val locationManager: MapLocationRuntimePort = mock()
        var shouldForward = false
        val binder = MapLocationFlightDataRuntimeBinder(
            liveFlightDataFlow = mock(),
            locationManager = locationManager,
            orientationProvider = { OrientationData(bearing = 12.0) },
            shouldForwardReplayLocationUpdate = { shouldForward }
        )
        val firstEligibleSample = sample(timestamp = 21L)
        val blockedSample = sample(timestamp = 22L)

        binder.applyFlightData(blockedSample)
        verifyNoInteractions(locationManager)

        shouldForward = true
        verifyNoInteractions(locationManager)

        binder.applyFlightData(firstEligibleSample)
        verify(locationManager).updateLocationFromReplayFrame(
            eq(firstEligibleSample.toReplayLocationFrame()),
            eq(OrientationData(bearing = 12.0))
        )

        clearInvocations(locationManager)
        shouldForward = false
        verifyNoInteractions(locationManager)

        binder.applyFlightData(sample(timestamp = 23L))
        verifyNoInteractions(locationManager)
    }

    @Test
    fun applyFlightData_readsLatestOrientationPerEmissionWithoutExtraTriggers() {
        val locationManager: MapLocationRuntimePort = mock()
        var orientation = OrientationData(bearing = 90.0, timestamp = 1L)
        val binder = MapLocationFlightDataRuntimeBinder(
            liveFlightDataFlow = mock(),
            locationManager = locationManager,
            orientationProvider = { orientation },
            shouldForwardReplayLocationUpdate = { true }
        )
        val initialSample = sample(timestamp = 31L)
        val updatedSample = sample(timestamp = 32L, track = 140.0)

        binder.applyFlightData(initialSample)
        verify(locationManager).updateLocationFromReplayFrame(
            eq(initialSample.toReplayLocationFrame()),
            eq(orientation)
        )

        clearInvocations(locationManager)
        orientation = OrientationData(bearing = 180.0, timestamp = 2L)
        verifyNoInteractions(locationManager)

        binder.applyFlightData(updatedSample)
        verify(locationManager).updateLocationFromReplayFrame(
            eq(updatedSample.toReplayLocationFrame()),
            eq(orientation)
        )
    }

    private fun sample(
        timestamp: Long,
        track: Double = 123.0
    ): RealTimeFlightData =
        RealTimeFlightData(
            latitude = -37.8,
            longitude = 145.0,
            track = track,
            groundSpeed = 42.0,
            accuracy = 6.0,
            gpsAltitude = 1200.0,
            timestamp = timestamp
        )
}
