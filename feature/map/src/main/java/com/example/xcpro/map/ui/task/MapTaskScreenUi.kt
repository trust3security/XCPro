package com.example.xcpro.map.ui.task

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.tasks.SwipeableTaskBottomSheet
import com.example.xcpro.tasks.TaskMinimizedIndicator

/**
 * Compose wrappers for AAT / task UI surfaces.
 * Lives under map/ui/task to keep presentation separate per CODING_POLICY.
 */
object MapTaskScreenUi {

    internal object Tags {
        const val TASK_BOTTOM_SHEET = "map_task_bottom_sheet"
        const val TASK_MINIMIZED_INDICATOR = "map_task_minimized_indicator"
    }

    @Composable
    fun TaskSearchOverlay(
        taskScreenManager: MapTaskScreenManager,
        allWaypoints: List<WaypointData>,
        onGoto: (WaypointData) -> Unit,
        modifier: Modifier = Modifier
    ) {
        // SEARCHBAR FEATURE REMOVED – keep hook for future reinstatement.
        // No-op for now.
    }

    @Composable
    fun TaskBottomSheet(
        taskScreenManager: MapTaskScreenManager,
        allWaypoints: List<WaypointData>,
        currentQNH: String,
        modifier: Modifier = Modifier,
        bottomSheetContent: (@Composable BoxScope.() -> Unit)? = null
    ) {
        val showBottomSheet by taskScreenManager.showTaskBottomSheet.collectAsStateWithLifecycle()
        val isSearchActive by taskScreenManager.showTaskScreen.collectAsStateWithLifecycle()
        val initialHeight by taskScreenManager.taskBottomSheetInitialHeight.collectAsStateWithLifecycle()

        if (showBottomSheet) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .zIndex(25f)
                    .testTag(Tags.TASK_BOTTOM_SHEET)
            ) {
                if (bottomSheetContent != null) {
                    bottomSheetContent()
                } else {
                    SwipeableTaskBottomSheet(
                        taskManager = taskScreenManager.taskManager,
                        mapLibreMap = taskScreenManager.mapState.mapLibreMap,
                        allWaypoints = allWaypoints,
                        isSearchActive = isSearchActive,
                        currentQNH = currentQNH,
                        initialHeight = initialHeight,
                        onClearTask = taskScreenManager::handleTaskClear,
                        onSaveTask = taskScreenManager::handleTaskSave,
                        onDismiss = taskScreenManager::hideTaskBottomSheet,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    @Composable
    fun TaskMinimizedIndicatorOverlay(
        taskScreenManager: MapTaskScreenManager,
        modifier: Modifier = Modifier,
        indicatorContent: (@Composable BoxScope.() -> Unit)? = null,
        showBottomSheetOverride: Boolean? = null,
        currentTaskOverride: com.example.xcpro.tasks.core.Task? = null,
        currentLocation: GPSData? = null
    ) {
        val showBottomSheet by taskScreenManager.showTaskBottomSheet.collectAsStateWithLifecycle()
        val currentTask = currentTaskOverride ?: taskScreenManager.taskManager.currentTask
        val isBottomSheetVisible = showBottomSheetOverride ?: showBottomSheet
        if (!isBottomSheetVisible && currentTask.waypoints.isNotEmpty()) {
            val currentGpsLocation =
                if (indicatorContent == null) {
                    currentLocation?.let { location ->
                        Pair(location.latLng.latitude, location.latLng.longitude)
                    }
                } else {
                    null
                }

            Box(
                modifier = modifier
                    .wrapContentWidth(Alignment.CenterHorizontally, unbounded = false)
                    .wrapContentHeight(Alignment.Top, unbounded = false)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .zIndex(3.8f)
                    .testTag(Tags.TASK_MINIMIZED_INDICATOR)
            ) {
                if (indicatorContent != null) {
                    indicatorContent()
                } else {
                    TaskMinimizedIndicator(
                        task = currentTask,
                        taskManager = taskScreenManager.taskManager,
                        currentGPSLocation = currentGpsLocation,
                        onClick = taskScreenManager::handleMinimizedIndicatorClick,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun AllTaskScreenComponents(
        taskScreenManager: MapTaskScreenManager,
        allWaypoints: List<WaypointData>,
        currentQNH: String,
        onWaypointGoto: (WaypointData) -> Unit,
        modifier: Modifier = Modifier,
        currentLocation: GPSData? = null
    ) {
        TaskSearchOverlay(
            taskScreenManager = taskScreenManager,
            allWaypoints = allWaypoints,
            onGoto = onWaypointGoto,
            modifier = modifier
        )

        TaskBottomSheet(
            taskScreenManager = taskScreenManager,
            allWaypoints = allWaypoints,
            currentQNH = currentQNH,
            modifier = modifier
        )

        TaskMinimizedIndicatorOverlay(
            taskScreenManager = taskScreenManager,
            modifier = modifier,
            currentLocation = currentLocation
        )
    }
}
