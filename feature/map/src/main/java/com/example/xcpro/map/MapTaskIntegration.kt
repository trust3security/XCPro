package com.example.xcpro.map

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.util.Log
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.core.TaskType

/**
 * MapTaskIntegration - Task-type specific UI components for MapScreen
 * Extracted from MapScreen.kt to maintain complete separation between Racing and AAT
 *
 * ZERO CROSS-CONTAMINATION:
 * - Racing tasks: No special UI components needed
 * - AAT tasks: Edit mode FAB, pin dragging support
 */
object MapTaskIntegration {

    private const val TAG = "MapTaskIntegration"

    /**
     * AAT Edit Mode FAB - ONLY for AAT tasks
     * Racing tasks do NOT use this component
     *
     * Maintains complete separation - FAB only appears when:
     * 1. isAATEditMode == true
     * 2. taskType == TaskType.AAT
     */
    @Composable
    fun AATEditModeFAB(
        isAATEditMode: Boolean,
        taskManager: TaskManagerCoordinator,
        cameraManager: MapCameraManager,
        onExitEditMode: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        // ✅ Task-type guard: Only show for AAT tasks
        if (!isAATEditMode || taskManager.taskType != TaskType.AAT) {
            return
        }

        FloatingActionButton(
            onClick = {
                onExitEditMode()
                taskManager.exitAATEditMode()
                // ✅ Restore camera position (zoom out to overview) - same as double-click exit
                cameraManager.restoreAATCameraPosition()
                Log.d(TAG, "🎯 FAB: Exited AAT edit mode and restored camera zoom")
            },
            containerColor = MaterialTheme.colorScheme.error,
            modifier = modifier
                .padding(16.dp)
                .zIndex(10f)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Exit AAT Edit Mode",
                tint = Color.White
            )
        }
    }

    /**
     * Racing Task UI Components
     * Currently no special UI components needed for Racing tasks
     * All Racing task display is handled by RacingTaskDisplay in the racing module
     */
    @Composable
    fun RacingTaskUI(
        taskManager: TaskManagerCoordinator,
        modifier: Modifier = Modifier
    ) {
        // Racing tasks use standard map display - no special overlay UI needed
        // RacingTaskDisplay handles all visual rendering on the map
    }

    /**
     * Get task-specific requirements for drawer gesture blocking
     * AAT edit mode blocks drawer, Racing does not
     */
    fun shouldBlockDrawerGestures(
        taskType: TaskType,
        isAATEditMode: Boolean
    ): Boolean {
        return when (taskType) {
            TaskType.AAT -> isAATEditMode // AAT blocks drawer during edit mode
            TaskType.RACING -> false // Racing never blocks drawer
        }
    }
}


