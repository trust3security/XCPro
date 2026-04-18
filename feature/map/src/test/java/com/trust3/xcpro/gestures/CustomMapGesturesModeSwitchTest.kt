package com.trust3.xcpro.gestures

import com.trust3.xcpro.common.flight.FlightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomMapGesturesModeSwitchTest {

    @Test
    fun resolveGestureModeSwitchTarget_cyclesForwardWithinVisibleModes() {
        val nextMode = resolveGestureModeSwitchTarget(
            currentMode = FlightMode.THERMAL,
            visibleModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL, FlightMode.FINAL_GLIDE),
            dragX = 300f
        )

        assertEquals(FlightMode.FINAL_GLIDE, nextMode)
    }

    @Test
    fun resolveGestureModeSwitchTarget_cyclesBackwardWithinVisibleModes() {
        val nextMode = resolveGestureModeSwitchTarget(
            currentMode = FlightMode.THERMAL,
            visibleModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL, FlightMode.FINAL_GLIDE),
            dragX = -300f
        )

        assertEquals(FlightMode.CRUISE, nextMode)
    }

    @Test
    fun resolveGestureModeSwitchTarget_returnsNullWhenVisibleModesMissingCurrentMode() {
        val nextMode = resolveGestureModeSwitchTarget(
            currentMode = FlightMode.THERMAL,
            visibleModes = listOf(FlightMode.CRUISE, FlightMode.FINAL_GLIDE),
            dragX = 300f
        )

        assertNull(nextMode)
    }

    @Test
    fun resolveGestureModeSwitchTarget_returnsNullWhenVisibleModesEmpty() {
        val nextMode = resolveGestureModeSwitchTarget(
            currentMode = FlightMode.CRUISE,
            visibleModes = emptyList(),
            dragX = 300f
        )

        assertNull(nextMode)
    }
}
