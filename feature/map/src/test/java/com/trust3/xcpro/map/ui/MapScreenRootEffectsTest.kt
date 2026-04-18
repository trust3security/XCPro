package com.trust3.xcpro.map.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapScreenRootEffectsTest {

    @Test
    fun quantizeOverlayOwnshipAltitudeMeters_returnsNullForNullOrNonFinite() {
        assertNull(quantizeOverlayOwnshipAltitudeMeters(null))
        assertNull(quantizeOverlayOwnshipAltitudeMeters(Double.NaN))
        assertNull(quantizeOverlayOwnshipAltitudeMeters(Double.POSITIVE_INFINITY))
    }

    @Test
    fun quantizeOverlayOwnshipAltitudeMeters_roundsToNearestTwoMetersByDefault() {
        assertEquals(1000.0, quantizeOverlayOwnshipAltitudeMeters(999.2) ?: error("expected value"), 0.0)
        assertEquals(1000.0, quantizeOverlayOwnshipAltitudeMeters(1000.7) ?: error("expected value"), 0.0)
        assertEquals(1002.0, quantizeOverlayOwnshipAltitudeMeters(1001.2) ?: error("expected value"), 0.0)
    }

    @Test
    fun quantizeOverlayOwnshipAltitudeMeters_returnsRawWhenStepInvalid() {
        assertEquals(
            1001.37,
            quantizeOverlayOwnshipAltitudeMeters(1001.37, quantizeStepMeters = 0.0) ?: error("expected value"),
            0.0
        )
        assertEquals(
            1001.37,
            quantizeOverlayOwnshipAltitudeMeters(1001.37, quantizeStepMeters = Double.NaN) ?: error("expected value"),
            0.0
        )
    }
}
