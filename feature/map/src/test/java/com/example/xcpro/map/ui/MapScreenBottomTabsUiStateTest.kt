package com.example.xcpro.map.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapScreenBottomTabsUiStateTest {

    @Test
    fun resolveMapBottomTab_invalidName_defaultsToSkySight() {
        assertEquals(MapBottomTab.SKYSIGHT, resolveMapBottomTab("not-a-tab"))
    }

    @Test
    fun resolveLastNonSatelliteMapStyleName_satellite_keepsPreviousStyle() {
        assertEquals(
            "Topo",
            resolveLastNonSatelliteMapStyleName(
                currentMapStyleName = SATELLITE_MAP_STYLE_NAME,
                previousLastNonSatelliteMapStyleName = "Topo"
            )
        )
    }

    @Test
    fun resolveLastNonSatelliteMapStyleName_nonSatellite_usesCurrentStyle() {
        assertEquals(
            "Terrain",
            resolveLastNonSatelliteMapStyleName(
                currentMapStyleName = "Terrain",
                previousLastNonSatelliteMapStyleName = "Topo"
            )
        )
    }

    @Test
    fun shouldHideBottomTabsSheet_trueWhenTaskPanelVisible() {
        assertTrue(
            shouldHideBottomTabsSheet(
                isTaskPanelVisible = true,
                hasTrafficDetailsOpen = false
            )
        )
    }

    @Test
    fun shouldHideBottomTabsSheet_trueWhenTrafficDetailsOpen() {
        assertTrue(
            shouldHideBottomTabsSheet(
                isTaskPanelVisible = false,
                hasTrafficDetailsOpen = true
            )
        )
    }

    @Test
    fun shouldHideBottomTabsSheet_falseWhenNeitherConditionApplies() {
        assertFalse(
            shouldHideBottomTabsSheet(
                isTaskPanelVisible = false,
                hasTrafficDetailsOpen = false
            )
        )
    }
}
