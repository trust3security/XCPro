package com.example.xcpro.map

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.example.xcpro.tasks.BottomSheetState
import com.example.xcpro.WaypointData
import com.example.xcpro.SearchWaypoint
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.tasks.TaskManagerCoordinator
// import com.example.xcpro.tasks.core.TaskSearchBarsOverlay // REMOVED - searchbar feature disabled
import com.example.xcpro.tasks.SwipeableTaskBottomSheet
import com.example.xcpro.tasks.TaskMinimizedIndicator
import org.maplibre.android.maps.MapLibreMap

/**
 * Centralized task screen management for MapScreen
 * Handles task search overlay, bottom sheet, and minimized indicator
 */
class MapTaskScreenManager(
    internal val mapState: MapScreenState,
    internal val taskManager: TaskManagerCoordinator
) {
    companion object {
        private const val TAG = "MapTaskScreenManager"
    }

    // Task Screen State
    var showTaskScreen: Boolean by mutableStateOf(false)
        private set

    var showTaskBottomSheet: Boolean by mutableStateOf(false)
        private set

    var taskBottomSheetInitialHeight: BottomSheetState by mutableStateOf(BottomSheetState.HALF_EXPANDED)
        private set

    /**
     * Show task search screen
     */
    fun showTaskSearch() {
        showTaskScreen = true
        Log.d(TAG, "Task search screen opened")
    }

    /**
     * Hide task search screen
     */
    fun hideTaskSearch() {
        showTaskScreen = false
        Log.d(TAG, "Task search screen closed")
    }

    /**
     * Show task bottom sheet with specified height
     */
    fun showTaskBottomSheet(initialHeight: BottomSheetState = BottomSheetState.HALF_EXPANDED) {
        taskBottomSheetInitialHeight = initialHeight
        showTaskBottomSheet = true
        Log.d(TAG, "Task bottom sheet opened at $initialHeight")
    }

    /**
     * Hide task bottom sheet
     */
    fun hideTaskBottomSheet() {
        showTaskBottomSheet = false
        Log.d(TAG, "Task bottom sheet closed")
    }

    /**
     * Handle navigation drawer task selection
     */
    fun handleNavigationTaskSelection(item: String) {
        when (item) {
            "task" -> {
                showTaskSearch()
            }
            "add_task" -> {
                // Searchbar removed - directly toggle bottom sheet instead
                if (showTaskBottomSheet) {
                    hideTaskBottomSheet()
                } else {
                    showTaskBottomSheet(BottomSheetState.HALF_EXPANDED)
                }
            }
        }
        Log.d(TAG, "Navigation task selection handled: $item")
    }

    /**
     * Handle task search overlay close
     */
    fun handleTaskSearchClose() {
        hideTaskSearch()
        if (taskManager.currentTask.waypoints.isNotEmpty()) {
            showTaskBottomSheet(BottomSheetState.HALF_EXPANDED)
        }
    }

    /**
     * Handle waypoint goto from search
     */
    fun handleWaypointGoto() {
        Log.d("TASK_UX", "🎯 Setting bottom sheet to HALF_EXPANDED for search selection")
        showTaskBottomSheet(BottomSheetState.HALF_EXPANDED)
    }

    /**
     * Handle minimized indicator click
     */
    fun handleMinimizedIndicatorClick() {
        Log.d("TASK_UX", "🎯 Setting bottom sheet to FULLY_EXPANDED for minimized indicator click")
        showTaskBottomSheet(BottomSheetState.FULLY_EXPANDED)
    }

    /**
     * Handle task clear operation
     */
    fun handleTaskClear() {
        taskManager.clearTask()
        hideTaskBottomSheet()

        // Clear map layers
        mapState.mapLibreMap?.getStyle()?.let { style ->
            try {
                style.removeLayer("task-line-layer")
                style.removeSource("task-line-source")
                Log.d(TAG, "Task layers cleared from map")
            } catch (e: Exception) {
                // Layers don't exist, that's fine
                Log.d(TAG, "Task layers already cleared or don't exist")
            }
        }
    }

    /**
     * Handle task save operation
     */
    fun handleTaskSave() {
        // TODO: Implement save functionality
        Log.d(TAG, "🎯 TASK DEBUG: Save task requested")
    }

    /**
     * Handle back gesture - close current overlay
     */
    fun handleBackGesture(): Boolean {
        return when {
            showTaskScreen -> {
                hideTaskSearch()
                hideTaskBottomSheet()
                true
            }
            showTaskBottomSheet -> {
                hideTaskBottomSheet()
                true
            }
            else -> false
        }
    }
}

/**
 * Compose components for task screen UI
 */
object MapTaskScreenUI {

    /**
     * Task search overlay component
     */
    @Composable
    fun TaskSearchOverlay(
        taskScreenManager: MapTaskScreenManager,
        allWaypoints: List<WaypointData>,
        onGoto: (WaypointData) -> Unit,
        modifier: Modifier = Modifier
    ) {
        // SEARCHBAR FEATURE REMOVED
        // TaskSearchBarsOverlay no longer displayed
        // Users add waypoints directly from the task bottom sheet
        /*
        if (taskScreenManager.showTaskScreen) {
            TaskSearchBarsOverlay(
                allWaypoints = allWaypoints,
                taskManager = taskScreenManager.taskManager,
                onClose = {
                    taskScreenManager.handleTaskSearchClose()
                },
                onGoto = { searchWp ->
                    // Convert SearchWaypoint to WaypointData
                    val waypointData = WaypointData(
                        name = searchWp.title.split(" ")[0], // Extract name from title
                        code = searchWp.id,
                        country = "",
                        latitude = searchWp.lat,
                        longitude = searchWp.lon,
                        elevation = "",
                        style = 1,
                        runwayDirection = null,
                        runwayLength = null,
                        frequency = null,
                        description = null
                    )
                    onGoto(waypointData)
                    taskScreenManager.handleWaypointGoto()
                }
            )
        }
        */
    }

    /**
     * Task bottom sheet component
     */
    @Composable
    fun TaskBottomSheet(
        taskScreenManager: MapTaskScreenManager,
        allWaypoints: List<WaypointData>,
        currentQNH: String,
        modifier: Modifier = Modifier
    ) {
        if (taskScreenManager.showTaskBottomSheet) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .zIndex(25f)
            ) {
                SwipeableTaskBottomSheet(
                    taskManager = taskScreenManager.taskManager,
                    mapLibreMap = taskScreenManager.mapState.mapLibreMap,
                    allWaypoints = allWaypoints,
                    isSearchActive = taskScreenManager.showTaskScreen,
                    currentQNH = currentQNH,
                    initialHeight = taskScreenManager.taskBottomSheetInitialHeight,
                    onClearTask = {
                        taskScreenManager.handleTaskClear()
                    },
                    onSaveTask = {
                        taskScreenManager.handleTaskSave()
                    },
                    onDismiss = {
                        taskScreenManager.hideTaskBottomSheet()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    /**
     * Task minimized indicator component
     */
    @Composable
    fun TaskMinimizedIndicatorOverlay(
        taskScreenManager: MapTaskScreenManager,
        modifier: Modifier = Modifier
    ) {
        // Debug log for minimized indicator visibility
        println("🎯 MAPSCREEN DEBUG: showTaskBottomSheet=${taskScreenManager.showTaskBottomSheet}, waypoints=${taskScreenManager.taskManager.currentTask.waypoints.size}")

        if (!taskScreenManager.showTaskBottomSheet && taskScreenManager.taskManager.currentTask.waypoints.isNotEmpty()) {
            // Extract current GPS location for real-time distance updates
            val currentGPSLocation = taskScreenManager.mapState.flightDataManager?.liveFlightData?.let {
                Pair(it.latitude, it.longitude)
            }

            Box(
                modifier = modifier
                    .wrapContentWidth(Alignment.CenterHorizontally, unbounded = false)
                    .wrapContentHeight(Alignment.Top, unbounded = false)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .zIndex(3.8f)
            ) {
                TaskMinimizedIndicator(
                    task = taskScreenManager.taskManager.currentTask,
                    taskManager = taskScreenManager.taskManager,
                    currentGPSLocation = currentGPSLocation,  // Pass GPS for live distance
                    onClick = {
                        taskScreenManager.handleMinimizedIndicatorClick()
                    },
                    modifier = Modifier
                        .padding(top = 8.dp) // Position just under system status bar
                )
            }
        }
    }

    /**
     * Combined task screen UI components
     */
    @Composable
    fun AllTaskScreenComponents(
        taskScreenManager: MapTaskScreenManager,
        allWaypoints: List<WaypointData>,
        currentQNH: String,
        onWaypointGoto: (WaypointData) -> Unit,
        modifier: Modifier = Modifier
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
            modifier = modifier
        )
    }
}

