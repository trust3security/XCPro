package com.trust3.xcpro.ogn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnSubscriptionPolicyTest {

    @Test
    fun shouldReconnectByCenterMove_respectsThreshold() {
        val currentLat = -33.8688
        val currentLon = 151.2093
        val thresholdMeters = 75_000.0

        val nearLat = -33.5000
        val nearLon = 151.3000
        val farLat = -33.0000
        val farLon = 151.5000

        assertFalse(
            OgnSubscriptionPolicy.shouldReconnectByCenterMoveMeters(
                previousLat = currentLat,
                previousLon = currentLon,
                nextLat = nearLat,
                nextLon = nearLon,
                thresholdMeters = thresholdMeters
            )
        )
        assertTrue(
            OgnSubscriptionPolicy.shouldReconnectByCenterMoveMeters(
                previousLat = currentLat,
                previousLon = currentLon,
                nextLat = farLat,
                nextLon = farLon,
                thresholdMeters = thresholdMeters
            )
        )
    }

    @Test
    fun haversineMeters_matchesKnownDistance() {
        val meters = OgnSubscriptionPolicy.haversineMeters(0.0, 0.0, 0.5, 0.5)
        assertEquals(78_626.0, meters, 500.0)
    }

    @Test
    fun isInViewport_filtersByVisibleBounds() {
        val bounds = OgnViewportBounds(
            northLat = -33.5,
            southLat = -34.2,
            eastLon = 151.6,
            westLon = 150.8
        )

        assertTrue(OgnSubscriptionPolicy.isInViewport(-33.9, 151.1, bounds))
        assertFalse(OgnSubscriptionPolicy.isInViewport(-35.1, 151.1, bounds))
        assertFalse(OgnSubscriptionPolicy.isInViewport(-33.9, 152.3, bounds))
    }

    @Test
    fun isInViewport_handlesAntiMeridianBounds() {
        val bounds = OgnViewportBounds(
            northLat = 10.0,
            southLat = -10.0,
            eastLon = -170.0,
            westLon = 170.0
        )

        assertTrue(OgnSubscriptionPolicy.isInViewport(0.0, 175.0, bounds))
        assertTrue(OgnSubscriptionPolicy.isInViewport(0.0, -175.0, bounds))
        assertFalse(OgnSubscriptionPolicy.isInViewport(0.0, -120.0, bounds))
    }
}
