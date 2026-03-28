package com.example.xcpro.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnTrafficViewportDeclutterPolicyTest {

    @Test
    fun closeZoom_keepsNormalLabelsVisible() {
        val policy = resolveOgnTrafficViewportDeclutterPolicy(10.5f)

        assertTrue(policy.labelsVisible)
    }

    @Test
    fun midZoom_hidesNormalLabels() {
        val policy = resolveOgnTrafficViewportDeclutterPolicy(9.25f)

        assertFalse(policy.labelsVisible)
    }

    @Test
    fun wideZoom_usesRequestedPhaseOneScalingBands() {
        val midPolicy = resolveOgnTrafficViewportDeclutterPolicy(9.3f)
        val widePolicy = resolveOgnTrafficViewportDeclutterPolicy(8.3f)
        val farPolicy = resolveOgnTrafficViewportDeclutterPolicy(6.0f)

        assertTrue(midPolicy.iconScaleMultiplier == 0.88f)
        assertTrue(widePolicy.iconScaleMultiplier == 0.78f)
        assertTrue(farPolicy.iconScaleMultiplier == 0.68f)
    }

    @Test
    fun nonFiniteZoom_fallsBackToCloseZoomPolicy() {
        val policy = resolveOgnTrafficViewportDeclutterPolicy(Float.NaN)

        assertTrue(policy.labelsVisible)
        assertTrue(policy.iconScaleMultiplier == 1.0f)
    }
}
