package com.example.xcpro.map.ui

import android.util.Log
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import com.example.xcpro.tasks.core.TaskType
import kotlinx.coroutines.isActive

@Composable
internal fun MapScreenRuntimeEffects(
    taskType: TaskType,
    drawerState: DrawerState,
    isAATEditMode: Boolean,
    onExitAATEditMode: () -> Unit,
    snailTrailManager: SnailTrailManager,
    locationManager: LocationManager,
    featureFlags: MapFeatureFlags,
    trailUpdateResult: TrailUpdateResult?,
    trailSettings: TrailSettings,
    currentZoom: Float,
    suppressLiveGps: Boolean,
    currentFlightModeSelection: FlightModeSelection,
    orientationManager: MapOrientationManager
) {
    // GAA CRITICAL FIX: Reset AAT edit mode when task type changes
    LaunchedEffect(taskType, isAATEditMode) {
        if (taskType != TaskType.AAT && isAATEditMode) {
            Log.d(MapScreenRootTag, "Task type changed to $taskType - resetting AAT edit mode")
            onExitAATEditMode()
        }
    }

    // GAA Control drawer gestures based on task type and edit mode
    // Uses MapTaskIntegration to determine if drawer should be blocked
    LaunchedEffect(isAATEditMode, taskType) {
        val shouldBlock = MapTaskIntegration.shouldBlockDrawerGestures(
            taskType = taskType,
            isAATEditMode = isAATEditMode
        )

        if (shouldBlock) {
            // Close drawer if it's open and prevent it from opening
            if (drawerState.isOpen) {
                drawerState.close()
            }
            Log.d(MapScreenRootTag, "Task-specific drawer blocking active ($taskType)")
        } else {
            Log.d(MapScreenRootTag, "GAA Drawer gestures enabled")
        }
    }

    LaunchedEffect(
        trailUpdateResult,
        trailSettings,
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
        snailTrailManager.updateFromTrailUpdate(
            update = trailUpdateResult,
            settings = trailSettings,
            currentZoom = currentZoom,
            displayLocation = displayLocation,
            displayTimeMillis = displayTimeMillis
        )
    }

    LaunchedEffect(currentZoom) {
        snailTrailManager.onZoomChanged(currentZoom)
    }

    LaunchedEffect(suppressLiveGps) {
        if (!suppressLiveGps || featureFlags.useRenderFrameSync) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            val snapshot = locationManager.getDisplayPoseSnapshot()
            snailTrailManager.updateDisplayPose(
                displayLocation = snapshot?.location,
                displayTimeMillis = snapshot?.timestampMs,
                frameId = snapshot?.frameId
            )
        }
    }

    DisposableEffect(suppressLiveGps) {
        if (!suppressLiveGps || !featureFlags.useRenderFrameSync) {
            onDispose { locationManager.setDisplayPoseFrameListener(null) }
        } else {
            val listener: (LocationManager.DisplayPoseSnapshot) -> Unit = { snapshot ->
                snailTrailManager.updateDisplayPose(
                    displayLocation = snapshot.location,
                    displayTimeMillis = snapshot.timestampMs,
                    frameId = snapshot.frameId
                )
            }
            locationManager.setDisplayPoseFrameListener(listener)
            onDispose { locationManager.setDisplayPoseFrameListener(null) }
        }
    }

    // GAA Map FlightMode to FlightModeSelection using FlightDataManager
    LaunchedEffect(currentFlightModeSelection) {
        orientationManager.setFlightMode(currentFlightModeSelection)
    }
}

private const val MapScreenRootTag = "MapScreen"
