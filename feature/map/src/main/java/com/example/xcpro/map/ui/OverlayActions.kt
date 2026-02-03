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
import com.example.xcpro.tasks.TaskManagerCoordinator

@Composable
internal fun BoxScope.AatEditFab(
    isAATEditMode: Boolean,
    taskManager: TaskManagerCoordinator,
    cameraManager: MapCameraManager,
    onExitAATEditMode: () -> Unit
) {
    MapTaskIntegration.AATEditModeFAB(
        isAATEditMode = isAATEditMode,
        taskManager = taskManager,
        cameraManager = cameraManager,
        onExitEditMode = onExitAATEditMode,
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
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    onOffsetChange: (Offset) -> Unit,
    isUiEditMode: Boolean
) {
    MapUIWidgets.SideHamburgerMenu(
        widgetManager = widgetManager,
        hamburgerOffset = hamburgerOffset.value,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        onHamburgerTap = onHamburgerTap,
        onHamburgerLongPress = onHamburgerLongPress,
        onOffsetChange = onOffsetChange,
        isEditMode = isUiEditMode,
        modifier = Modifier
            .align(Alignment.TopStart)
            .zIndex(12f)
    )
}
