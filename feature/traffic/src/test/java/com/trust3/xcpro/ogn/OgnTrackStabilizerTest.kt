package com.trust3.xcpro.ogn

import org.junit.Assert.assertEquals
import org.junit.Test

class OgnTrackStabilizerTest {

    @Test
    fun stabilizeTrackDegrees_holdsPreviousWhenSpeedBelowThreshold() {
        val stabilized = stabilizeTrackDegrees(
            incomingTrackDegrees = 120.0,
            groundSpeedMps = 2.0,
            previousTrackDegrees = 100.0
        )

        assertEquals(100.0, stabilized!!, 1e-6)
    }

    @Test
    fun stabilizeTrackDegrees_ignoresTinyChangeAboveSpeedThreshold() {
        val stabilized = stabilizeTrackDegrees(
            incomingTrackDegrees = 102.5,
            groundSpeedMps = 10.0,
            previousTrackDegrees = 100.0
        )

        assertEquals(100.0, stabilized!!, 1e-6)
    }

    @Test
    fun stabilizeTrackDegrees_acceptsMeaningfulChangeAboveSpeedThreshold() {
        val stabilized = stabilizeTrackDegrees(
            incomingTrackDegrees = 106.0,
            groundSpeedMps = 10.0,
            previousTrackDegrees = 100.0
        )

        assertEquals(106.0, stabilized!!, 1e-6)
    }

    @Test
    fun stabilizeTrackDegrees_handlesWrapAroundTinyChange() {
        val stabilized = stabilizeTrackDegrees(
            incomingTrackDegrees = 1.0,
            groundSpeedMps = 10.0,
            previousTrackDegrees = 358.0
        )

        assertEquals(358.0, stabilized!!, 1e-6)
    }

    @Test
    fun stabilizeTrackDegrees_keepsIncomingWhenNoPreviousHeading() {
        val stabilized = stabilizeTrackDegrees(
            incomingTrackDegrees = 45.0,
            groundSpeedMps = 1.0,
            previousTrackDegrees = null
        )

        assertEquals(45.0, stabilized!!, 1e-6)
    }
}
