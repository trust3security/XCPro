package com.example.xcpro.map

import android.util.Log
import com.example.xcpro.tasks.BottomSheetState
import com.example.xcpro.tasks.TaskManagerCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Centralized task screen management for MapScreen
 * Handles task panel state and task-panel entrypoints from map UI.
 */
class MapTaskScreenManager(
    internal val mapState: MapScreenState,
    internal val taskManager: TaskManagerCoordinator
) {
    companion object {
        private const val TAG = "MapTaskScreenManager"
    }

    enum class TaskPanelState {
        HIDDEN,
        COLLAPSED,
        EXPANDED_PARTIAL,
        EXPANDED_FULL
    }

    private val _taskPanelState = MutableStateFlow(TaskPanelState.HIDDEN)
    val taskPanelState: StateFlow<TaskPanelState> = _taskPanelState.asStateFlow()

    /**
     * Compatibility read model for existing callsites:
     * true only when task panel content is expanded and visible.
     */
    val showTaskBottomSheet: Flow<Boolean> = taskPanelState
        .map { state ->
            state == TaskPanelState.EXPANDED_PARTIAL || state == TaskPanelState.EXPANDED_FULL
        }
        .distinctUntilChanged()

    /**
     * Handle navigation drawer task selection
     */
    fun handleNavigationTaskSelection(item: String) {
        when (item) {
            "add_task" -> {
                handleAddTaskToggle()
            }
        }
        Log.d(TAG, "Navigation task selection handled: $item")
    }

    /**
     * Handle minimized indicator click
     */
    fun handleMinimizedIndicatorClick() {
        Log.d("TASK_UX", "Setting task panel to EXPANDED_FULL for minimized indicator click")
        showTaskPanel(TaskPanelState.EXPANDED_FULL)
    }

    /**
     * Handle task clear operation
     */
    fun handleTaskClear() {
        taskManager.clearTask()
        hideTaskPanel()

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
            _taskPanelState.value != TaskPanelState.HIDDEN -> {
                hideTaskPanel()
                true
            }
            else -> false
        }
    }

    fun showTaskPanel(initialState: TaskPanelState = TaskPanelState.EXPANDED_PARTIAL) {
        _taskPanelState.value = normalizeState(initialState)
        Log.d(TAG, "Task panel opened at ${_taskPanelState.value}")
    }

    fun hideTaskPanel() {
        _taskPanelState.value = TaskPanelState.HIDDEN
        Log.d(TAG, "Task panel closed")
    }

    fun collapseTaskPanel() {
        _taskPanelState.value = if (taskManager.currentTask.waypoints.isNotEmpty()) {
            TaskPanelState.COLLAPSED
        } else {
            TaskPanelState.HIDDEN
        }
        Log.d(TAG, "Task panel collapsed to ${_taskPanelState.value}")
    }

    fun setPanelState(state: TaskPanelState) {
        _taskPanelState.value = normalizeState(state)
        Log.d(TAG, "Task panel state updated to ${_taskPanelState.value}")
    }

    /**
     * Backward-compatibility shim while callsites migrate from BottomSheetState.
     */
    fun showTaskBottomSheet(initialHeight: BottomSheetState = BottomSheetState.HALF_EXPANDED) {
        val target = when (initialHeight) {
            BottomSheetState.MINIMIZED -> TaskPanelState.COLLAPSED
            BottomSheetState.HALF_EXPANDED -> TaskPanelState.EXPANDED_PARTIAL
            BottomSheetState.FULLY_EXPANDED -> TaskPanelState.EXPANDED_FULL
        }
        showTaskPanel(target)
    }

    /**
     * Backward-compatibility shim while callsites migrate from bottom-sheet naming.
     */
    fun hideTaskBottomSheet() {
        hideTaskPanel()
    }

    private fun handleAddTaskToggle() {
        val next = when (_taskPanelState.value) {
            TaskPanelState.HIDDEN -> TaskPanelState.EXPANDED_PARTIAL
            TaskPanelState.COLLAPSED -> TaskPanelState.EXPANDED_PARTIAL
            TaskPanelState.EXPANDED_PARTIAL -> TaskPanelState.HIDDEN
            TaskPanelState.EXPANDED_FULL -> TaskPanelState.HIDDEN
        }
        _taskPanelState.value = normalizeState(next)
        Log.d(TAG, "Add Task toggled panel to ${_taskPanelState.value}")
    }

    private fun normalizeState(state: TaskPanelState): TaskPanelState {
        return if (state == TaskPanelState.COLLAPSED && taskManager.currentTask.waypoints.isEmpty()) {
            TaskPanelState.HIDDEN
        } else {
            state
        }
    }
}

