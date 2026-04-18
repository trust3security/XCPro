package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Test

class OgnTrafficViewportSizingTest {

    @Test
    fun closeZoom_keepsBaseSizeAtMinimumPreference() {
        val sizing = resolveOgnTrafficViewportSizing(
            baseIconSizePx = OGN_ICON_SIZE_MIN_PX,
            zoomLevel = 10.6f
        )

        assertEquals(OGN_ICON_SIZE_MIN_PX, sizing.renderedIconSizePx)
        assertEquals(1.0f, sizing.iconScaleMultiplier, 0.0001f)
    }

    @Test
    fun midZoom_reducesDefaultBaseSize() {
        val sizing = resolveOgnTrafficViewportSizing(
            baseIconSizePx = OGN_ICON_SIZE_DEFAULT_PX,
            zoomLevel = 9.6f
        )

        assertEquals(109, sizing.renderedIconSizePx)
        assertEquals(0.88f, sizing.iconScaleMultiplier, 0.0001f)
    }

    @Test
    fun wideZoom_reducesMaximumBaseSizeWithBoundedMinimum() {
        val sizing = resolveOgnTrafficViewportSizing(
            baseIconSizePx = OGN_ICON_SIZE_MAX_PX,
            zoomLevel = 7.8f
        )

        assertEquals(169, sizing.renderedIconSizePx)
        assertEquals(0.68f, sizing.iconScaleMultiplier, 0.0001f)
    }

    @Test
    fun veryWideZoom_clampsRenderedSizeToViewportMinimum() {
        val sizing = resolveOgnTrafficViewportSizing(
            baseIconSizePx = OGN_ICON_SIZE_DEFAULT_PX,
            zoomLevel = 6.0f
        )

        assertEquals(84, sizing.renderedIconSizePx)
        assertEquals(0.68f, sizing.iconScaleMultiplier, 0.0001f)
    }

    @Test
    fun nonFiniteZoom_fallsBackToBaseSize() {
        val sizing = resolveOgnTrafficViewportSizing(
            baseIconSizePx = OGN_ICON_SIZE_DEFAULT_PX,
            zoomLevel = Float.NaN
        )

        assertEquals(OGN_ICON_SIZE_DEFAULT_PX, sizing.renderedIconSizePx)
        assertEquals(1.0f, sizing.iconScaleMultiplier, 0.0001f)
    }

    @Test
    fun nullZoom_fallsBackToBaseSize() {
        val sizing = resolveOgnTrafficViewportSizing(
            baseIconSizePx = OGN_ICON_SIZE_DEFAULT_PX,
            zoomLevel = null
        )

        assertEquals(OGN_ICON_SIZE_DEFAULT_PX, sizing.renderedIconSizePx)
        assertEquals(1.0f, sizing.iconScaleMultiplier, 0.0001f)
    }

    @Test
    fun renderedIconClamp_allowsViewportSizesBelowPreferenceMinimum() {
        assertEquals(48, clampOgnRenderedIconSizePx(12))
        assertEquals(63, clampOgnRenderedIconSizePx(63))
    }

    @Test
    fun baseSize_isClampedBeforeViewportScaling() {
        val sizing = resolveOgnTrafficViewportSizing(
            baseIconSizePx = 999,
            zoomLevel = 8.6f
        )

        assertEquals(193, sizing.renderedIconSizePx)
        assertEquals(0.78f, sizing.iconScaleMultiplier, 0.0001f)
    }
}
