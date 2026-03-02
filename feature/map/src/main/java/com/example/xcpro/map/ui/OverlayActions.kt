package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ui.widgets.MapUIWidgets
import com.example.xcpro.tasks.core.TaskType

@Composable
internal fun BoxScope.AatEditFab(
    isAATEditMode: Boolean,
    taskType: TaskType,
    cameraManager: MapCameraManager,
    onExitAATEditMode: () -> Unit,
    onSyncTaskVisuals: () -> Unit
) {
    MapTaskIntegration.AATEditModeFAB(
        isAATEditMode = isAATEditMode,
        taskType = taskType,
        cameraManager = cameraManager,
        onExitEditMode = onExitAATEditMode,
        onSyncTaskVisuals = onSyncTaskVisuals,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .zIndex(11f)
    )
}

@Composable
internal fun BoxScope.HamburgerMenu(
    widgetManager: MapUIWidgetManager,
    hamburgerOffset: MutableState<Offset>,
    screenWidthPx: Float,
    screenHeightPx: Float,
    hamburgerSizePx: MutableState<Float>,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    onOffsetChange: (Offset) -> Unit,
    onSizeChange: (Float) -> Unit,
    isUiEditMode: Boolean
) {
    MapUIWidgets.SideHamburgerMenu(
        widgetManager = widgetManager,
        hamburgerOffset = hamburgerOffset.value,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        sizePx = hamburgerSizePx.value,
        onHamburgerTap = onHamburgerTap,
        onHamburgerLongPress = onHamburgerLongPress,
        onOffsetChange = onOffsetChange,
        onSizeChange = onSizeChange,
        isEditMode = isUiEditMode,
        modifier = Modifier
            .align(Alignment.TopStart)
            .zIndex(12f)
    )
}

@Composable
internal fun BoxScope.SettingsShortcut(
    widgetManager: MapUIWidgetManager,
    settingsOffset: MutableState<Offset>,
    screenWidthPx: Float,
    screenHeightPx: Float,
    settingsSizePx: MutableState<Float>,
    onSettingsTap: () -> Unit,
    onOffsetChange: (Offset) -> Unit,
    onSizeChange: (Float) -> Unit,
    isUiEditMode: Boolean
) {
    MapUIWidgets.SettingsShortcut(
        widgetManager = widgetManager,
        settingsOffset = settingsOffset.value,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        sizePx = settingsSizePx.value,
        onSettingsTap = onSettingsTap,
        onOffsetChange = onOffsetChange,
        onSizeChange = onSizeChange,
        isEditMode = isUiEditMode,
        modifier = Modifier
            .align(Alignment.TopStart)
            .zIndex(12f)
    )
}
