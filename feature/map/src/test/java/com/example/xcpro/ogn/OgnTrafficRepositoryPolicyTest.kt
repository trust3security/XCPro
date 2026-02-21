package com.example.xcpro.ogn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnTrafficRepositoryPolicyTest {

    @Test
    fun isWithinReceiveRadiusKm_prefersRequestedCenterWhenAvailable() {
        val within = isWithinReceiveRadiusKm(
            targetLat = -1.34,
            targetLon = 0.0,
            requestedCenterLat = 0.09,
            requestedCenterLon = 0.0,
            subscriptionCenterLat = 0.0,
            subscriptionCenterLon = 0.0,
            radiusKm = 150.0
        )

        assertFalse(within)
    }

    @Test
    fun isWithinReceiveRadiusKm_fallsBackToSubscriptionCenterWhenRequestedCenterMissing() {
        val within = isWithinReceiveRadiusKm(
            targetLat = 1.0,
            targetLon = 0.0,
            requestedCenterLat = null,
            requestedCenterLon = null,
            subscriptionCenterLat = 0.0,
            subscriptionCenterLon = 0.0,
            radiusKm = 150.0
        )

        assertTrue(within)
    }

    @Test
    fun parseLogrespStatus_detectsVerified() {
        val status = parseLogrespStatus("# logresp OGNXC1 verified, server GLIDERN1")
        assertEquals(OgnLogrespStatus.VERIFIED, status)
    }

    @Test
    fun parseLogrespStatus_detectsUnverified() {
        val status = parseLogrespStatus("# logresp OGNXC1 unverified, server GLIDERN1")
        assertEquals(OgnLogrespStatus.UNVERIFIED, status)
    }

    @Test
    fun parseLogrespStatus_ignoresNonLogrespLines() {
        val status = parseLogrespStatus("# aprsc 2.1.20-gdaa359f")
        assertNull(status)
    }
}
