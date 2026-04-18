package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlueLocationViewportScalePolicyTest {

    @Test
    fun visibleRadius_usesNearestScreenEdgeDistance() {
        val radiusMeters = resolveBlueLocationVisibleRadiusMeters(
            screenX = 300f,
            screenY = 500f,
            viewportMetrics = MapCameraViewportMetrics(widthPx = 1_000, heightPx = 2_000, pixelRatio = 2f),
            distancePerPixelMeters = 10.0
        )

        assertEquals(3_000.0, radiusMeters ?: 0.0, 0.0)
    }

    @Test
    fun visibleRadius_returnsNullWhenOwnshipIsOutsideViewport() {
        val radiusMeters = resolveBlueLocationVisibleRadiusMeters(
            screenX = -1f,
            screenY = 500f,
            viewportMetrics = MapCameraViewportMetrics(widthPx = 1_000, heightPx = 2_000, pixelRatio = 2f),
            distancePerPixelMeters = 10.0
        )

        assertNull(radiusMeters)
    }

    @Test
    fun closeRadius_usesReducedTriangleSize() {
        val policy = resolveBlueLocationViewportScalePolicy(4_999.0)

        assertEquals(BLUE_LOCATION_SMALL_ICON_SCALE_MULTIPLIER, policy.iconScaleMultiplier, 0f)
    }

    @Test
    fun fiveKilometers_reducesTriangleByTwentyFivePercent() {
        val policy = resolveBlueLocationViewportScalePolicy(5_000.0)

        assertEquals(BLUE_LOCATION_SMALL_ICON_SCALE_MULTIPLIER, policy.iconScaleMultiplier, 0f)
    }

    @Test
    fun tenKilometers_usesTheReducedTriangleSize() {
        val policy = resolveBlueLocationViewportScalePolicy(10_000.0)

        assertEquals(BLUE_LOCATION_SMALL_ICON_SCALE_MULTIPLIER, policy.iconScaleMultiplier, 0f)
    }

    @Test
    fun twentyKilometers_stillUsesTheReducedTriangleSize() {
        val policy = resolveBlueLocationViewportScalePolicy(20_000.0)

        assertEquals(BLUE_LOCATION_SMALL_ICON_SCALE_MULTIPLIER, policy.iconScaleMultiplier, 0f)
    }

    @Test
    fun invalidOrUnavailableRadius_stillUsesReducedTriangleSize() {
        assertEquals(BLUE_LOCATION_SMALL_ICON_SCALE_MULTIPLIER, resolveBlueLocationViewportScalePolicy(null).iconScaleMultiplier, 0f)
        assertEquals(BLUE_LOCATION_SMALL_ICON_SCALE_MULTIPLIER, resolveBlueLocationViewportScalePolicy(Double.NaN).iconScaleMultiplier, 0f)
        assertEquals(BLUE_LOCATION_SMALL_ICON_SCALE_MULTIPLIER, resolveBlueLocationViewportScalePolicy(Double.POSITIVE_INFINITY).iconScaleMultiplier, 0f)
        assertEquals(BLUE_LOCATION_SMALL_ICON_SCALE_MULTIPLIER, resolveBlueLocationViewportScalePolicy(0.0).iconScaleMultiplier, 0f)
        assertEquals(BLUE_LOCATION_SMALL_ICON_SCALE_MULTIPLIER, resolveBlueLocationViewportScalePolicy(-1.0).iconScaleMultiplier, 0f)
    }
}
