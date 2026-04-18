package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbTrafficOverlaySupportTest {

    @Test
    fun adsbPackedGroupCollisionSizePx_usesOutlineHaloWhenLargerThanMinimum() {
        val collisionSizePx = adsbPackedGroupCollisionSizePx(
            iconSizePx = 60,
            viewportPolicy = viewportPolicy(iconScaleMultiplier = 1f),
            density = 1f
        )

        assertEquals(68.4f, collisionSizePx, 0.001f)
    }

    @Test
    fun adsbPackedGroupCollisionSizePx_respectsMinimumFloor() {
        val collisionSizePx = adsbPackedGroupCollisionSizePx(
            iconSizePx = 24,
            viewportPolicy = viewportPolicy(iconScaleMultiplier = 0.88f),
            density = 2f
        )

        assertEquals(80f, collisionSizePx, 0.001f)
    }

    private fun viewportPolicy(
        iconScaleMultiplier: Float
    ) = AdsbTrafficViewportDeclutterPolicy(
        iconScaleMultiplier = iconScaleMultiplier,
        showAllLabels = false,
        closeTrafficLabelDistanceMeters = 10_000.0,
        maxTargets = 72
    )
}
