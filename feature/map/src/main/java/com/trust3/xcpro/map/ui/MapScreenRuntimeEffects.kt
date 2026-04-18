package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dfcards.FlightModeSelection
import com.trust3.xcpro.map.MapLocationRuntimePort
import com.trust3.xcpro.map.trail.SnailTrailManager
import com.trust3.xcpro.map.trail.TrailSettings
import com.trust3.xcpro.map.trail.domain.TrailUpdateResult
import com.trust3.xcpro.tasks.core.TaskType
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun MapScreenRuntimeEffects(
    taskType: TaskType,
    drawerState: androidx.compose.material3.DrawerState,
    isAATEditMode: Boolean,
    onExitAATEditMode: () -> Unit,
    snailTrailManager: SnailTrailManager,
    locationManager: MapLocationRuntimePort,
    trailUpdateResult: TrailUpdateResult?,
    trailSettings: TrailSettings,
    currentZoomFlow: StateFlow<Float>,
    renderLocalOwnship: Boolean,
    currentFlightModeSelection: FlightModeSelection,
    onApplyOrientationFlightModeSelection: (FlightModeSelection) -> Unit
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
        trailUpdateResult = trailUpdateResult,
        trailSettings = trailSettings,
        currentZoom = currentZoom,
        renderLocalOwnship = renderLocalOwnship
    )
    MapScreenOrientationRuntimeEffects(
        currentFlightModeSelection = currentFlightModeSelection,
        onApplyOrientationFlightModeSelection = onApplyOrientationFlightModeSelection
    )
}

internal const val MapScreenRuntimeEffectsTag = "MapScreen"
