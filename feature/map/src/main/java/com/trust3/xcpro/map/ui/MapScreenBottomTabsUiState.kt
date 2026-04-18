package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trust3.xcpro.map.MapTaskScreenManager

internal data class MapScreenBottomTabsUiState(
    val selectedBottomTab: MapBottomTab,
    val isBottomTabsSheetVisible: Boolean,
    val isTaskPanelVisible: Boolean,
    val setSelectedBottomTabName: (String) -> Unit,
    val setBottomTabsSheetVisible: (Boolean) -> Unit
)

internal fun resolveMapBottomTab(selectedBottomTabName: String): MapBottomTab =
    runCatching { MapBottomTab.valueOf(selectedBottomTabName) }
        .getOrDefault(MapBottomTab.SKYSIGHT)

internal fun shouldHideBottomTabsSheet(
    isTaskPanelVisible: Boolean,
    hasTrafficDetailsOpen: Boolean
): Boolean = isTaskPanelVisible || hasTrafficDetailsOpen

internal fun shouldSuspendBottomTabsSheetForGeneralSettings(
    isGeneralSettingsVisible: Boolean,
    isBottomTabsSheetVisible: Boolean
): Boolean = isGeneralSettingsVisible && isBottomTabsSheetVisible

internal fun shouldRestoreBottomTabsSheetAfterGeneralSettings(
    isGeneralSettingsVisible: Boolean,
    restoreAfterGeneralSettings: Boolean,
    isTaskPanelVisible: Boolean,
    hasTrafficDetailsOpen: Boolean
): Boolean {
    return !isGeneralSettingsVisible &&
        restoreAfterGeneralSettings &&
        !shouldHideBottomTabsSheet(
            isTaskPanelVisible = isTaskPanelVisible,
            hasTrafficDetailsOpen = hasTrafficDetailsOpen
        )
}

@Composable
internal fun rememberMapScreenBottomTabsUiState(
    taskScreenManager: MapTaskScreenManager,
    hasTrafficDetailsOpen: Boolean,
    isGeneralSettingsVisible: Boolean
): MapScreenBottomTabsUiState {
    var selectedBottomTabName by rememberSaveable { mutableStateOf(MapBottomTab.SKYSIGHT.name) }
    var isBottomTabsSheetVisible by rememberSaveable { mutableStateOf(false) }
    var restoreBottomTabsSheetAfterGeneralSettings by rememberSaveable { mutableStateOf(false) }

    val selectedBottomTab = remember(selectedBottomTabName) {
        resolveMapBottomTab(selectedBottomTabName)
    }
    val taskPanelState by taskScreenManager.taskPanelState.collectAsStateWithLifecycle()
    val isTaskPanelVisible = taskPanelState != MapTaskScreenManager.TaskPanelState.HIDDEN
    LaunchedEffect(isTaskPanelVisible, hasTrafficDetailsOpen, isGeneralSettingsVisible) {
        if (shouldHideBottomTabsSheet(isTaskPanelVisible, hasTrafficDetailsOpen)) {
            isBottomTabsSheetVisible = false
            restoreBottomTabsSheetAfterGeneralSettings = false
        } else if (
            shouldSuspendBottomTabsSheetForGeneralSettings(
                isGeneralSettingsVisible = isGeneralSettingsVisible,
                isBottomTabsSheetVisible = isBottomTabsSheetVisible
            )
        ) {
            restoreBottomTabsSheetAfterGeneralSettings = true
            isBottomTabsSheetVisible = false
        } else if (
            shouldRestoreBottomTabsSheetAfterGeneralSettings(
                isGeneralSettingsVisible = isGeneralSettingsVisible,
                restoreAfterGeneralSettings = restoreBottomTabsSheetAfterGeneralSettings,
                isTaskPanelVisible = isTaskPanelVisible,
                hasTrafficDetailsOpen = hasTrafficDetailsOpen
            )
        ) {
            restoreBottomTabsSheetAfterGeneralSettings = false
            isBottomTabsSheetVisible = true
        } else if (!isGeneralSettingsVisible && restoreBottomTabsSheetAfterGeneralSettings) {
            restoreBottomTabsSheetAfterGeneralSettings = false
        }
    }

    return MapScreenBottomTabsUiState(
        selectedBottomTab = selectedBottomTab,
        isBottomTabsSheetVisible = isBottomTabsSheetVisible,
        isTaskPanelVisible = isTaskPanelVisible,
        setSelectedBottomTabName = { selectedBottomTabName = it },
        setBottomTabsSheetVisible = { isBottomTabsSheetVisible = it }
    )
}
