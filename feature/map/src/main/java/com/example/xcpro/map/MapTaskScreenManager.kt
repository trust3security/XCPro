package com.example.xcpro.map

import android.util.Log
import com.example.xcpro.tasks.BottomSheetState
import com.example.xcpro.tasks.TaskManagerCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val _showTaskScreen = MutableStateFlow(false)
    val showTaskScreen: StateFlow<Boolean> = _showTaskScreen.asStateFlow()

    private val _showTaskBottomSheet = MutableStateFlow(false)
    val showTaskBottomSheet: StateFlow<Boolean> = _showTaskBottomSheet.asStateFlow()

    private val _taskBottomSheetInitialHeight = MutableStateFlow(BottomSheetState.HALF_EXPANDED)
    val taskBottomSheetInitialHeight: StateFlow<BottomSheetState> =
        _taskBottomSheetInitialHeight.asStateFlow()

    /**
     * Show task search screen
     */
    fun showTaskSearch() {
        _showTaskScreen.value = true
        Log.d(TAG, "Task search screen opened")
    }

    /**
     * Hide task search screen
     */
    fun hideTaskSearch() {
        _showTaskScreen.value = false
        Log.d(TAG, "Task search screen closed")
    }

    /**
     * Show task bottom sheet with specified height
     */
    fun showTaskBottomSheet(initialHeight: BottomSheetState = BottomSheetState.HALF_EXPANDED) {
        _taskBottomSheetInitialHeight.value = initialHeight
        _showTaskBottomSheet.value = true
        Log.d(TAG, "Task bottom sheet opened at $initialHeight")
    }

    /**
     * Hide task bottom sheet
     */
    fun hideTaskBottomSheet() {
        _showTaskBottomSheet.value = false
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
                if (_showTaskBottomSheet.value) {
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
        Log.d("TASK_UX", " Setting bottom sheet to HALF_EXPANDED for search selection")
        showTaskBottomSheet(BottomSheetState.HALF_EXPANDED)
    }

    /**
     * Handle minimized indicator click
     */
    fun handleMinimizedIndicatorClick() {
        Log.d("TASK_UX", " Setting bottom sheet to FULLY_EXPANDED for minimized indicator click")
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
        Log.d(TAG, " TASK DEBUG: Save task requested")
    }

    /**
     * Handle back gesture - close current overlay
     */
    fun handleBackGesture(): Boolean {
        return when {
            _showTaskScreen.value -> {
                hideTaskSearch()
                hideTaskBottomSheet()
                true
            }
            _showTaskBottomSheet.value -> {
                hideTaskBottomSheet()
                true
            }
            else -> false
        }
    }
}

