package com.example.xcpro.map

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.gestures.CustomMapGestureHandler
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.tasks.core.TaskType
import org.maplibre.android.geometry.LatLng

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
    private const val ATTRIBUTION_PASSTHROUGH_WIDTH_DP = 180f
    private const val ATTRIBUTION_PASSTHROUGH_HEIGHT_DP = 72f

    /**
     * Setup custom gesture handler with task-type specific handling
     * Maintains complete separation between Racing and AAT gesture logic
     */
    @Composable
    fun GestureHandlerOverlay(
        mapState: MapScreenState,
        taskType: TaskType,
        visibleModes: List<FlightMode>,
        locationManager: LocationManager,
        cameraManager: MapCameraManager,
        currentMode: FlightMode,
        onModeChange: (FlightMode) -> Unit,
        currentLocation: MapLocationUiModel?,
        showReturnButton: Boolean,
        isAATEditMode: Boolean,
        createTaskGestureHandler: (TaskGestureCallbacks) -> TaskGestureHandler,
        onEnterAATEditMode: (Int) -> Unit,
        onExitAATEditMode: () -> Unit,
        onUpdateAATTargetPoint: (Int, Double, Double) -> Unit,
        onSyncTaskVisuals: () -> Unit,
        onMapTap: (LatLng) -> Unit = {},
        onMapLongPress: (LatLng) -> Unit = {},
        gestureRegions: List<MapGestureRegion> = emptyList(),
        modifier: Modifier = Modifier
    ) {
        val density = LocalDensity.current
        val pixelRatio = mapState.mapView?.pixelRatio ?: density.density
        val attributionPassthroughWidthPx = with(density) { ATTRIBUTION_PASSTHROUGH_WIDTH_DP.dp.toPx() }
        val attributionPassthroughHeightPx =
            with(density) { ATTRIBUTION_PASSTHROUGH_HEIGHT_DP.dp.toPx() }
        val gestureCallbacks = remember(
            cameraManager,
            onEnterAATEditMode,
            onExitAATEditMode,
            onUpdateAATTargetPoint,
            onSyncTaskVisuals
        ) {
            TaskGestureCallbacks(
                onEnterEditMode = { waypointIndex, lat, lon, radiusKm ->
                    cameraManager.zoomToAATAreaForEdit(lat, lon, radiusKm)
                    onEnterAATEditMode(waypointIndex)
                    onSyncTaskVisuals()
                    Log.d(TAG, "Entered AAT edit mode for waypoint $waypointIndex")
                },
                onExitEditMode = {
                    cameraManager.restoreAATCameraPosition()
                    onExitAATEditMode()
                    onSyncTaskVisuals()
                    Log.d(TAG, "Exited AAT edit mode")
                },
                onDragTarget = { waypointIndex, lat, lon ->
                    onUpdateAATTargetPoint(waypointIndex, lat, lon)
                    onSyncTaskVisuals()
                }
            )
        }
        val taskGestureHandler = remember(taskType, gestureCallbacks) {
            createTaskGestureHandler(gestureCallbacks)
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
                visibleModes = visibleModes,
                taskGestureHandler = taskGestureHandler,
                gestureRegions = gestureRegions,
                onMapTap = onMapTap,
                onMapLongPress = onMapLongPress,
                mapViewPixelRatio = pixelRatio,
                attributionTapPassthroughWidthPx = attributionPassthroughWidthPx,
                attributionTapPassthroughHeightPx = attributionPassthroughHeightPx,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}



