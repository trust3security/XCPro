package com.example.xcpro.map.ui

import android.util.Log
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.core.TaskType
import kotlinx.coroutines.isActive

@Composable
internal fun MapScreenRuntimeEffects(
    taskManager: TaskManagerCoordinator,
    drawerState: DrawerState,
    isAATEditMode: Boolean,
    onExitAATEditMode: () -> Unit,
    snailTrailManager: SnailTrailManager,
    locationManager: LocationManager,
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
        val displayLocation = if (suppressLiveGps) {
            locationManager.getDisplayPoseLocation()
        } else {
            null
        }
        val displayTimeMillis = if (suppressLiveGps) {
            locationManager.getDisplayPoseTimestampMs()
        } else {
            null
        }
        snailTrailManager.updateFromFlightData(
            liveData = liveFlightData,
            isFlying = isFlying,
            isReplay = suppressLiveGps,
            settings = trailSettings,
            currentZoom = currentZoom,
            displayLocation = displayLocation,
            displayTimeMillis = displayTimeMillis
        )
    }


    LaunchedEffect(suppressLiveGps) {
        if (!suppressLiveGps) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            snailTrailManager.updateDisplayPose(
                displayLocation = locationManager.getDisplayPoseLocation(),
                displayTimeMillis = locationManager.getDisplayPoseTimestampMs()
            )
        }
    }

    // GAœAÿ Map FlightMode to FlightModeSelection using FlightDataManager
    LaunchedEffect(currentFlightModeSelection) {
        orientationManager.setFlightMode(currentFlightModeSelection)
    }
}

private const val MapScreenRootTag = "MapScreen"
