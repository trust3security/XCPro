package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Test

class OgnTargetRingOverlaySizingTest {

    @Test
    fun targetRingRadius_wrapsDefaultGliderIconMoreTightly() {
        assertEquals(28.52f, resolveOgnTargetRingRadiusPx(OGN_ICON_SIZE_DEFAULT_PX), 0.01f)
    }

    @Test
    fun targetRingSizing_usesRenderedIconClampBounds() {
        assertEquals(11.04f, resolveOgnTargetRingRadiusPx(1), 0.01f)
        assertEquals(1.0f, resolveOgnTargetRingStrokeWidthPx(1), 0.01f)
    }

    @Test
    fun targetRingStroke_scalesWithIconWithoutGettingTooThick() {
        assertEquals(1.94f, resolveOgnTargetRingStrokeWidthPx(OGN_ICON_SIZE_DEFAULT_PX), 0.01f)
    }

    @Test
    fun targetRingSizing_tracksZoomDerivedRenderedIconBands() {
        val closeSizePx = resolveOgnTrafficViewportSizing(
            baseIconSizePx = OGN_ICON_SIZE_DEFAULT_PX,
            zoomLevel = 11.0f
        ).renderedIconSizePx
        val midSizePx = resolveOgnTrafficViewportSizing(
            baseIconSizePx = OGN_ICON_SIZE_DEFAULT_PX,
            zoomLevel = 9.5f
        ).renderedIconSizePx
        val wideSizePx = resolveOgnTrafficViewportSizing(
            baseIconSizePx = OGN_ICON_SIZE_DEFAULT_PX,
            zoomLevel = 8.6f
        ).renderedIconSizePx

        val closeRadiusPx = resolveOgnTargetRingRadiusPx(closeSizePx)
        val midRadiusPx = resolveOgnTargetRingRadiusPx(midSizePx)
        val wideRadiusPx = resolveOgnTargetRingRadiusPx(wideSizePx)

        val closeStrokePx = resolveOgnTargetRingStrokeWidthPx(closeSizePx)
        val midStrokePx = resolveOgnTargetRingStrokeWidthPx(midSizePx)
        val wideStrokePx = resolveOgnTargetRingStrokeWidthPx(wideSizePx)

        assertEquals(28.52f, closeRadiusPx, 0.01f)
        assertEquals(25.07f, midRadiusPx, 0.01f)
        assertEquals(22.31f, wideRadiusPx, 0.01f)
        assertEquals(1.94f, closeStrokePx, 0.01f)
        assertEquals(1.70f, midStrokePx, 0.01f)
        assertEquals(1.52f, wideStrokePx, 0.01f)
    }
}
