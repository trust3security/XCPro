package com.trust3.xcpro.gestures

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomMapGesturesAttributionPassThroughTest {

    @Test
    fun bypassEnabled_whenTapInBottomStartRegion() {
        assertTrue(
            shouldBypassAttributionTap(
                pointerX = 24f,
                pointerY = 760f,
                viewportHeightPx = 800f,
                passthroughWidthPx = 180f,
                passthroughHeightPx = 72f
            )
        )
    }

    @Test
    fun bypassDisabled_whenTapOutsideRegion() {
        assertFalse(
            shouldBypassAttributionTap(
                pointerX = 220f,
                pointerY = 760f,
                viewportHeightPx = 800f,
                passthroughWidthPx = 180f,
                passthroughHeightPx = 72f
            )
        )
        assertFalse(
            shouldBypassAttributionTap(
                pointerX = 24f,
                pointerY = 500f,
                viewportHeightPx = 800f,
                passthroughWidthPx = 180f,
                passthroughHeightPx = 72f
            )
        )
    }
}
