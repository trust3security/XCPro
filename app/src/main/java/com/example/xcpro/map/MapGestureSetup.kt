package com.example.xcpro.map

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.example.xcpro.FlightMode
import com.example.xcpro.gestures.CustomMapGestureHandler
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.aat.models.AATLatLng

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
        currentLocation: GPSData?,
        showReturnButton: Boolean,
        isAATEditMode: Boolean,
        onAATEditModeChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier
    ) {
        // ✅ CRITICAL FIX: Remember AAT waypoints and recompute when task type or waypoints change
        val aatWaypoints = androidx.compose.runtime.remember(taskManager.taskType) {
            getAATWaypointsForGestures(taskManager)
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .zIndex(1.5f) // Below cards (2f) so they can receive long press, but above map
        ) {
            CustomMapGestureHandler(
                mapLibreMap = mapState.mapLibreMap,
                currentMode = mapState.currentMode,
                onModeChange = { newMode ->
                    Log.d(TAG, "🔄 onModeChange callback called with: ${newMode.displayName}")
                    mapState.updateFlightMode(newMode)
                    flightDataManager.updateFlightModeFromEnum(newMode)
                    Log.d(TAG, "✅ mapState.currentMode should now be: ${mapState.currentMode.displayName}")
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
                // ✅ AAT-specific gesture support (delegated to separate function)
                taskType = taskManager.taskType,
                aatWaypoints = aatWaypoints,
                isAATEditMode = isAATEditMode,
                onAATLongPress = { waypointIndex ->
                    onAATEditModeChange(true)

                    // Get waypoint coordinates and radius for dynamic zoom
                    val waypoint = aatWaypoints.getOrNull(waypointIndex)
                    if (waypoint != null) {
                        // Extract AAT area radius from custom parameters
                        val areaRadiusKm = (waypoint.customParameters["aatAreaRadiusKm"] as? Double) ?: 10.0

                        // Zoom to turnpoint with calculated zoom level based on area size
                        cameraManager.zoomToAATAreaForEdit(waypoint.lat, waypoint.lon, areaRadiusKm)
                        Log.d(TAG, "🎯 AAT: Zoomed to turnpoint ${waypoint.title} (radius=${areaRadiusKm}km) for edit mode")
                    }

                    taskManager.enterAATEditMode(waypointIndex)
                    Log.d(TAG, "🎯 Entered AAT edit mode for waypoint $waypointIndex")
                },
                onAATExitEditMode = {
                    onAATEditModeChange(false)

                    // Restore camera position to where user was before edit mode
                    cameraManager.restoreAATCameraPosition()
                    Log.d(TAG, "🎯 AAT: Restored camera position after exiting edit mode")

                    taskManager.exitAATEditMode()
                    Log.d(TAG, "🎯 Exited AAT edit mode")
                },
                onAATDrag = { waypointIndex, newPosition ->
                    taskManager.updateAATTargetPoint(waypointIndex, newPosition.latitude, newPosition.longitude)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    /**
     * Get AAT waypoints with area data for gesture detection
     * ZERO DEPENDENCIES on Racing - AAT-only extraction
     *
     * Returns empty list if task type is not AAT (Racing tasks don't need this)
     */
    private fun getAATWaypointsForGestures(taskManager: TaskManagerCoordinator): List<TaskWaypoint> {
        if (taskManager.taskType != TaskType.AAT) {
            return emptyList()
        }

        return try {
            taskManager.getAATTaskManager().currentAATTask.waypoints.map { aatWp ->
                TaskWaypoint(
                    id = aatWp.id,
                    title = aatWp.title,
                    subtitle = "", // Not used for hit detection
                    lat = aatWp.lat,
                    lon = aatWp.lon,
                    role = when (aatWp.role) {
                        com.example.xcpro.tasks.aat.models.AATWaypointRole.START -> WaypointRole.START
                        com.example.xcpro.tasks.aat.models.AATWaypointRole.TURNPOINT -> WaypointRole.TURNPOINT
                        com.example.xcpro.tasks.aat.models.AATWaypointRole.FINISH -> WaypointRole.FINISH
                    },
                    customParameters = mapOf(
                        "aatAreaRadiusKm" to (aatWp.assignedArea.radiusMeters / 1000.0)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting AAT waypoints for gestures: ${e.message}")
            emptyList()
        }
    }
}


