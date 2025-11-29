package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ui.widgets.MapUIWidgets
import com.example.xcpro.tasks.TaskManagerCoordinator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

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
internal fun BoxScope.ReplayDevFab(
    onReplayPickFileClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onReplayPickFileClick,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = 96.dp, end = 16.dp)
            .zIndex(15f)
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Pick and start IGC replay"
        )
    }
}

@Composable
internal fun BoxScope.HamburgerMenu(
    widgetManager: MapUIWidgetManager,
    hamburgerOffset: MutableState<Offset>,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    isUiEditMode: Boolean
) {
    MapUIWidgets.SideHamburgerMenu(
        widgetManager = widgetManager,
        hamburgerOffset = hamburgerOffset.value,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        onHamburgerTap = onHamburgerTap,
        onHamburgerLongPress = onHamburgerLongPress,
        onOffsetChange = { offset -> hamburgerOffset.value = offset },
        isEditMode = isUiEditMode,
        modifier = Modifier
            .align(Alignment.TopStart)
            .zIndex(12f)
    )
}
