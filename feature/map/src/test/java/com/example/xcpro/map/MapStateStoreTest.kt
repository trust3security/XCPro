package com.example.xcpro.map

import com.example.xcpro.common.flight.FlightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.xcpro.map.trail.TrailLength
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.TrailType

class MapStateStoreTest {

    @Test
    fun updateSafeContainerSize_ignoresZero() {
        val store = MapStateStore(initialStyleName = "Topo")

        store.updateSafeContainerSize(MapSize.Zero)

        assertEquals(MapSize.Zero, store.safeContainerSize.value)
    }

    @Test
    fun setBaseMapStyle_reportsBaseAndEffectiveChanges() {
        val store = MapStateStore(initialStyleName = "Topo")

        val unchanged = store.setBaseMapStyle("Topo")
        val changed = store.setBaseMapStyle("Satellite")

        assertFalse(unchanged.baseStyleChanged)
        assertFalse(unchanged.effectiveStyleChanged)
        assertTrue(changed.baseStyleChanged)
        assertTrue(changed.effectiveStyleChanged)
        assertEquals("Satellite", store.mapStyleName.value)
    }

    @Test
    fun forecastSatelliteOverride_usesEffectiveStyleWithoutChangingBaseStyle() {
        val store = MapStateStore(initialStyleName = "Topo")

        assertTrue(store.setForecastSatelliteOverrideEnabled(true))

        assertEquals("Topo", store.baseMapStyleName.value)
        assertEquals("Satellite", store.mapStyleName.value)
    }

    @Test
    fun selectingBaseStyle_clearsThermallingContrastOverride() {
        val store = MapStateStore(initialStyleName = "Topo")

        store.setThermallingContrastOverrideEnabled(true)

        val result = store.setBaseMapStyle("Terrain")

        assertTrue(result.baseStyleChanged)
        assertTrue(result.effectiveStyleChanged)
        assertFalse(store.thermallingContrastOverrideEnabled.value)
        assertEquals("Terrain", store.baseMapStyleName.value)
        assertEquals("Terrain", store.mapStyleName.value)
    }

    @Test
    fun setShowDistanceCircles_updatesState() {
        val store = MapStateStore(initialStyleName = "Topo")

        store.setShowDistanceCircles(true)

        assertTrue(store.showDistanceCircles.value)
    }

    @Test
    fun setTrailSettings_updatesState() {
        val store = MapStateStore(initialStyleName = "Topo")
        val settings = TrailSettings(
            length = TrailLength.SHORT,
            type = TrailType.VARIO_2_DOTS,
            windDriftEnabled = false,
            scalingEnabled = false
        )

        store.setTrailSettings(settings)

        assertEquals(settings, store.trailSettings.value)
    }

    @Test
    fun startupFlightModeState_isConservativeUntilHydrated() {
        val store = MapStateStore(initialStyleName = "Topo")

        assertEquals(listOf(FlightMode.CRUISE), store.visibleFlightModes.value)
        assertEquals(FlightMode.CRUISE, store.currentMode.value)
    }

    @Test
    fun applyFlightModeUiState_updatesRequestedRuntimeVisibleAndEffectiveMode() {
        val store = MapStateStore(initialStyleName = "Topo")
        val state = MapFlightModeUiState(
            requestedMode = FlightMode.FINAL_GLIDE,
            runtimeOverrideMode = FlightMode.THERMAL,
            effectiveMode = FlightMode.THERMAL,
            effectiveModeSource = MapFlightModeSource.RUNTIME_OVERRIDE,
            visibleModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL),
            requestedModeVisible = false,
            runtimeOverrideVisible = true
        )

        store.applyFlightModeUiState(state)

        assertEquals(FlightMode.FINAL_GLIDE, store.requestedFlightMode.value)
        assertEquals(FlightMode.THERMAL, store.runtimeFlightModeOverride.value)
        assertEquals(listOf(FlightMode.CRUISE, FlightMode.THERMAL), store.visibleFlightModes.value)
        assertEquals(FlightMode.THERMAL, store.currentMode.value)
        assertEquals(MapFlightModeSource.RUNTIME_OVERRIDE, store.effectiveFlightModeSource.value)
    }
}
