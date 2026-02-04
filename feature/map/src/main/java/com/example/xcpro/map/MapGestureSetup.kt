package com.example.xcpro.map

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.gestures.CustomMapGestureHandler
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.tasks.TaskManagerCoordinator

/**
 * MapGestureSetup - Centralized gesture handling configuration
 * Extracted from MapScreen.kt to reduce file size and improve modularity
 *
 * Handles:
 * - Custom gesture detection (pan, zoom, mode switching)
 * - AAT long press and drag gestures (task-type specific)
 * - Racing task gesture handling (no special gestures needed)
 */
object MapGestureSetup {

    private const val TAG = "MapGestureSetup"

    /**
     * Setup custom gesture handler with task-type specific handling
     * Maintains complete separation between Racing and AAT gesture logic
     */
    @Composable
    fun GestureHandlerOverlay(
        mapState: MapScreenState,
        taskManager: TaskManagerCoordinator,
        flightDataManager: FlightDataManager,
        locationManager: LocationManager,
        cameraManager: MapCameraManager,
        currentMode: FlightMode,
        onModeChange: (FlightMode) -> Unit,
        currentLocation: MapLocationUiModel?,
        showReturnButton: Boolean,
        isAATEditMode: Boolean,
        onAATEditModeChange: (Boolean) -> Unit,
        gestureRegions: List<MapGestureRegion> = emptyList(),
        modifier: Modifier = Modifier
    ) {
        val taskType by taskManager.taskTypeFlow.collectAsStateWithLifecycle()
        val pixelRatio = mapState.mapView?.pixelRatio ?: LocalDensity.current.density
        val gestureCallbacks = remember(cameraManager, taskManager, onAATEditModeChange) {
            TaskGestureCallbacks(
                onEnterEditMode = { waypointIndex, lat, lon, radiusKm ->
                    onAATEditModeChange(true)
                    cameraManager.zoomToAATAreaForEdit(lat, lon, radiusKm)
                    taskManager.enterAATEditMode(waypointIndex)
                    Log.d(TAG, "Entered AAT edit mode for waypoint $waypointIndex")
                },
                onExitEditMode = {
                    onAATEditModeChange(false)
                    cameraManager.restoreAATCameraPosition()
                    taskManager.exitAATEditMode()
                    Log.d(TAG, "Exited AAT edit mode")
                },
                onDragTarget = { waypointIndex, lat, lon ->
                    taskManager.updateAATTargetPoint(waypointIndex, lat, lon)
                }
            )
        }
        val taskGestureHandler = remember(taskType, gestureCallbacks) {
            taskManager.createGestureHandler(gestureCallbacks)
        }

        LaunchedEffect(taskGestureHandler, isAATEditMode) {
            taskGestureHandler.onExternalEditModeChanged(isAATEditMode)
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .zIndex(1.5f) // Below cards (2f) so they can receive long press, but above map
        ) {
            CustomMapGestureHandler(
                mapLibreMap = mapState.mapLibreMap,
                currentMode = currentMode,
                onModeChange = { newMode ->
                    Log.d(TAG, " onModeChange callback called with: ${newMode.displayName}")
                    onModeChange(newMode)
                },
                showReturnButton = showReturnButton,
                onShowReturnButton = { show ->
                    if (show) locationManager.showReturnButton()
                },
                currentLocation = currentLocation,
                onSaveLocation = { location, zoom, bearing ->
                    locationManager.handleUserInteraction(location, zoom, bearing)
                },
                visibleModes = flightDataManager.visibleModes,
                taskGestureHandler = taskGestureHandler,
                gestureRegions = gestureRegions,
                mapViewPixelRatio = pixelRatio,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}



