package com.example.xcpro.map.ui

import android.util.Log
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.core.TaskType

@Composable
internal fun MapScreenRuntimeEffects(
    taskManager: TaskManagerCoordinator,
    drawerState: DrawerState,
    isAATEditMode: Boolean,
    onExitAATEditMode: () -> Unit,
    snailTrailManager: SnailTrailManager,
    liveFlightData: RealTimeFlightData?,
    trailSettings: TrailSettings,
    currentZoom: Float,
    isFlying: Boolean,
    suppressLiveGps: Boolean,
    currentFlightModeSelection: FlightModeSelection,
    orientationManager: MapOrientationManager
) {
    // GAœAÿ CRITICAL FIX: Reset AAT edit mode when task type changes
    LaunchedEffect(taskManager.taskType, isAATEditMode) {
        if (taskManager.taskType != TaskType.AAT && isAATEditMode) {
            Log.d(MapScreenRootTag, "=’'AA§ Task type changed to ${taskManager.taskType} - resetting AAT edit mode")
            onExitAATEditMode()
        }
    }

    // GAœAÿ Control drawer gestures based on task type and edit mode
    // Uses MapTaskIntegration to determine if drawer should be blocked
    LaunchedEffect(isAATEditMode, taskManager.taskType) {
        val shouldBlock = MapTaskIntegration.shouldBlockDrawerGestures(
            taskType = taskManager.taskType,
            isAATEditMode = isAATEditMode
        )

        if (shouldBlock) {
            // Close drawer if it's open and prevent it from opening
            if (drawerState.isOpen) {
                drawerState.close()
            }
            Log.d(MapScreenRootTag, "=’'AoA« Task-specific drawer blocking active (${taskManager.taskType})")
        } else {
            Log.d(MapScreenRootTag, "GAœAÿ Drawer gestures enabled")
        }
    }

    LaunchedEffect(
        liveFlightData,
        trailSettings,
        currentZoom,
        isFlying,
        suppressLiveGps
    ) {
        snailTrailManager.updateFromFlightData(
            liveData = liveFlightData,
            isFlying = isFlying,
            isReplay = suppressLiveGps,
            settings = trailSettings,
            currentZoom = currentZoom
        )
    }

    // GAœAÿ Map FlightMode to FlightModeSelection using FlightDataManager
    LaunchedEffect(currentFlightModeSelection) {
        orientationManager.setFlightMode(currentFlightModeSelection)
    }
}

private const val MapScreenRootTag = "MapScreen"
