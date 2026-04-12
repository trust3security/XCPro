package com.example.xcpro.map

import com.example.dfcards.FlightModeSelection
import com.example.xcpro.common.flight.FlightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFlightModePolicyTest {

    @Test
    fun emptyVisibilityMap_showsAllModesInOrder() {
        val state = resolveMapFlightModeUiState(
            requestedMode = FlightMode.THERMAL,
            runtimeOverrideMode = null,
            modeVisibilities = emptyMap()
        )

        assertEquals(
            listOf(FlightMode.CRUISE, FlightMode.THERMAL, FlightMode.FINAL_GLIDE),
            state.visibleModes
        )
        assertTrue(state.requestedModeVisible)
        assertFalse(state.runtimeOverrideVisible)
        assertEquals(FlightMode.THERMAL, state.effectiveMode)
        assertEquals(MapFlightModeSource.REQUESTED, state.effectiveModeSource)
    }

    @Test
    fun cruiseCannotBeHidden() {
        val state = resolveMapFlightModeUiState(
            requestedMode = FlightMode.CRUISE,
            runtimeOverrideMode = null,
            modeVisibilities = mapOf(FlightModeSelection.CRUISE to false)
        )

        assertEquals(
            listOf(FlightMode.CRUISE, FlightMode.THERMAL, FlightMode.FINAL_GLIDE),
            state.visibleModes
        )
        assertTrue(state.requestedModeVisible)
        assertEquals(FlightMode.CRUISE, state.effectiveMode)
    }

    @Test
    fun hiddenRequestedThermal_fallsBackToCruise() {
        val state = resolveMapFlightModeUiState(
            requestedMode = FlightMode.THERMAL,
            runtimeOverrideMode = null,
            modeVisibilities = mapOf(FlightModeSelection.THERMAL to false)
        )

        assertFalse(state.requestedModeVisible)
        assertNull(state.runtimeOverrideMode)
        assertEquals(FlightMode.CRUISE, state.effectiveMode)
        assertEquals(MapFlightModeSource.FALLBACK_CRUISE, state.effectiveModeSource)
    }

    @Test
    fun hiddenRequestedFinalGlide_fallsBackToCruise() {
        val state = resolveMapFlightModeUiState(
            requestedMode = FlightMode.FINAL_GLIDE,
            runtimeOverrideMode = null,
            modeVisibilities = mapOf(FlightModeSelection.FINAL_GLIDE to false)
        )

        assertFalse(state.requestedModeVisible)
        assertEquals(FlightMode.CRUISE, state.effectiveMode)
        assertEquals(MapFlightModeSource.FALLBACK_CRUISE, state.effectiveModeSource)
    }

    @Test
    fun visibleRuntimeOverride_winsOverRequested() {
        val state = resolveMapFlightModeUiState(
            requestedMode = FlightMode.CRUISE,
            runtimeOverrideMode = FlightMode.THERMAL,
            modeVisibilities = emptyMap()
        )

        assertTrue(state.requestedModeVisible)
        assertTrue(state.runtimeOverrideVisible)
        assertEquals(FlightMode.THERMAL, state.effectiveMode)
        assertEquals(MapFlightModeSource.RUNTIME_OVERRIDE, state.effectiveModeSource)
    }

    @Test
    fun hiddenRuntimeOverride_withVisibleRequested_usesRequested() {
        val state = resolveMapFlightModeUiState(
            requestedMode = FlightMode.FINAL_GLIDE,
            runtimeOverrideMode = FlightMode.THERMAL,
            modeVisibilities = mapOf(FlightModeSelection.THERMAL to false)
        )

        assertFalse(state.runtimeOverrideVisible)
        assertTrue(state.requestedModeVisible)
        assertEquals(FlightMode.FINAL_GLIDE, state.effectiveMode)
        assertEquals(MapFlightModeSource.REQUESTED, state.effectiveModeSource)
    }

    @Test
    fun hiddenRuntimeOverride_andHiddenRequested_fallsBackToCruise() {
        val state = resolveMapFlightModeUiState(
            requestedMode = FlightMode.FINAL_GLIDE,
            runtimeOverrideMode = FlightMode.THERMAL,
            modeVisibilities = mapOf(
                FlightModeSelection.THERMAL to false,
                FlightModeSelection.FINAL_GLIDE to false
            )
        )

        assertFalse(state.runtimeOverrideVisible)
        assertFalse(state.requestedModeVisible)
        assertEquals(FlightMode.CRUISE, state.effectiveMode)
        assertEquals(MapFlightModeSource.FALLBACK_CRUISE, state.effectiveModeSource)
    }

    @Test
    fun visibleModesAlwaysStayInCanonicalOrder() {
        val state = resolveMapFlightModeUiState(
            requestedMode = FlightMode.CRUISE,
            runtimeOverrideMode = null,
            modeVisibilities = mapOf(
                FlightModeSelection.FINAL_GLIDE to true,
                FlightModeSelection.THERMAL to true,
                FlightModeSelection.CRUISE to true
            )
        )

        assertEquals(
            listOf(FlightMode.CRUISE, FlightMode.THERMAL, FlightMode.FINAL_GLIDE),
            state.visibleModes
        )
    }
}
