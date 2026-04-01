package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.map.MapLocationRuntimePort
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import com.example.xcpro.tasks.core.TaskType
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun MapScreenRuntimeEffects(
    taskType: TaskType,
    drawerState: androidx.compose.material3.DrawerState,
    isAATEditMode: Boolean,
    onExitAATEditMode: () -> Unit,
    snailTrailManager: SnailTrailManager,
    locationManager: MapLocationRuntimePort,
    featureFlags: MapFeatureFlags,
    trailUpdateResult: TrailUpdateResult?,
    trailSettings: TrailSettings,
    currentZoomFlow: StateFlow<Float>,
    suppressLiveGps: Boolean,
    currentFlightModeSelection: FlightModeSelection,
    orientationManager: MapOrientationManager
) {
    val currentZoom by currentZoomFlow.collectAsStateWithLifecycle()
    MapScreenTaskRuntimeEffects(
        taskType = taskType,
        drawerState = drawerState,
        isAATEditMode = isAATEditMode,
        onExitAATEditMode = onExitAATEditMode
    )
    MapScreenTrailRuntimeEffects(
        snailTrailManager = snailTrailManager,
        locationManager = locationManager,
        featureFlags = featureFlags,
        trailUpdateResult = trailUpdateResult,
        trailSettings = trailSettings,
        currentZoom = currentZoom,
        suppressLiveGps = suppressLiveGps
    )
    MapScreenOrientationRuntimeEffects(
        currentFlightModeSelection = currentFlightModeSelection,
        orientationManager = orientationManager
    )
}

internal const val MapScreenRuntimeEffectsTag = "MapScreen"
