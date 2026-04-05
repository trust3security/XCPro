package com.example.xcpro.map.ui

import com.example.xcpro.map.model.MapLocationUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MapScreenContentOverlaysTest {

    @Test
    fun resolveVisibleCurrentLocation_hidesLocation_whenLocalOwnshipIsDisabled() {
        assertNull(
            resolveVisibleCurrentLocation(
                renderLocalOwnship = false,
                currentLocation = sampleLocation()
            )
        )
    }

    @Test
    fun resolveVisibleCurrentLocation_keepsLocation_whenLocalOwnshipIsEnabled() {
        assertEquals(
            sampleLocation(),
            resolveVisibleCurrentLocation(
                renderLocalOwnship = true,
                currentLocation = sampleLocation()
            )
        )
    }

    @Test
    fun resolveTrafficPanelsOwnshipCoordinate_usesVisibleOwnshipLocationOnly() {
        val visibleCoordinate = resolveTrafficPanelsOwnshipCoordinate(
            renderLocalOwnship = true,
            currentLocation = sampleLocation()
        )
        assertNotNull(visibleCoordinate)
        assertEquals(-33.9, visibleCoordinate?.latitude ?: 0.0, 0.0)
        assertNull(
            resolveTrafficPanelsOwnshipCoordinate(
                renderLocalOwnship = false,
                currentLocation = sampleLocation()
            )
        )
    }

    private fun sampleLocation(): MapLocationUiModel = MapLocationUiModel(
        latitude = -33.9,
        longitude = 151.2,
        speedMs = 18.0,
        bearingDeg = 180.0,
        accuracyMeters = 4.0,
        timestampMs = 1_000L
    )
}
