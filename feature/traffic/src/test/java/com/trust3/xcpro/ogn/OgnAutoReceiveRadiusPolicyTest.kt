package com.trust3.xcpro.ogn

import org.junit.Assert.assertEquals
import org.junit.Test

class OgnAutoReceiveRadiusPolicyTest {

    @Test
    fun resolveRadiusKm_onGroundAndZoomedIn_prefersSmallestBucket() {
        val radiusKm = OgnAutoReceiveRadiusPolicy.resolveRadiusKm(
            OgnAutoReceiveRadiusContext(
                zoomLevel = 12.0f,
                groundSpeedMs = 0.0,
                isFlying = false
            )
        )

        assertEquals(40, radiusKm)
    }

    @Test
    fun resolveRadiusKm_flyingFastAndZoomedOut_prefersLargestBucket() {
        val radiusKm = OgnAutoReceiveRadiusPolicy.resolveRadiusKm(
            OgnAutoReceiveRadiusContext(
                zoomLevel = 6.0f,
                groundSpeedMs = 40.0,
                isFlying = true
            )
        )

        assertEquals(220, radiusKm)
    }

    @Test
    fun resolveRadiusKm_flyingSlowAndZoomedOut_usesMediumBucket() {
        val radiusKm = OgnAutoReceiveRadiusPolicy.resolveRadiusKm(
            OgnAutoReceiveRadiusContext(
                zoomLevel = 6.0f,
                groundSpeedMs = 8.0,
                isFlying = true
            )
        )

        assertEquals(150, radiusKm)
    }
}
