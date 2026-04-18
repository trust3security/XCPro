package com.trust3.xcpro.map.ui

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

    @Test
    fun shouldSuspendBottomTabsSheetForGeneralSettings_trueWhenGeneralOpensOverVisibleSheet() {
        assertTrue(
            shouldSuspendBottomTabsSheetForGeneralSettings(
                isGeneralSettingsVisible = true,
                isBottomTabsSheetVisible = true
            )
        )
    }

    @Test
    fun shouldSuspendBottomTabsSheetForGeneralSettings_falseWhenSheetAlreadyHidden() {
        assertFalse(
            shouldSuspendBottomTabsSheetForGeneralSettings(
                isGeneralSettingsVisible = true,
                isBottomTabsSheetVisible = false
            )
        )
    }

    @Test
    fun shouldRestoreBottomTabsSheetAfterGeneralSettings_trueWhenNoOtherBlockerRemains() {
        assertTrue(
            shouldRestoreBottomTabsSheetAfterGeneralSettings(
                isGeneralSettingsVisible = false,
                restoreAfterGeneralSettings = true,
                isTaskPanelVisible = false,
                hasTrafficDetailsOpen = false
            )
        )
    }

    @Test
    fun shouldRestoreBottomTabsSheetAfterGeneralSettings_falseWhenTaskPanelStillVisible() {
        assertFalse(
            shouldRestoreBottomTabsSheetAfterGeneralSettings(
                isGeneralSettingsVisible = false,
                restoreAfterGeneralSettings = true,
                isTaskPanelVisible = true,
                hasTrafficDetailsOpen = false
            )
        )
    }

    @Test
    fun shouldRestoreBottomTabsSheetAfterGeneralSettings_falseWhileGeneralStillVisible() {
        assertFalse(
            shouldRestoreBottomTabsSheetAfterGeneralSettings(
                isGeneralSettingsVisible = true,
                restoreAfterGeneralSettings = true,
                isTaskPanelVisible = false,
                hasTrafficDetailsOpen = false
            )
        )
    }
}
