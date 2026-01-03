package com.example.xcpro.map

import android.content.Context
import android.util.Log
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.screens.overlays.getMapStyleUrl
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.map.BlueLocationOverlay
import com.example.xcpro.map.DistanceCirclesOverlay
import com.example.xcpro.tasks.TaskManagerCoordinator
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadWaypointFiles
import com.example.xcpro.loadAndApplyWaypoints

class MapInitializer(
    private val context: Context,
    private val mapState: MapScreenState,
    private val mapStateStore: MapStateStore,
    private val stateActions: MapStateActions,
    private val orientationManager: MapOrientationManager,
    private val taskManager: TaskManagerCoordinator,
    private val unifiedSensorManager: UnifiedSensorManager
) {
    companion object {
        private const val TAG = "MapInitializer"
        private const val INITIAL_LATITUDE = 46.52
        private const val INITIAL_LONGITUDE = 6.63
        private const val INITIAL_ZOOM = 8.0
    }

    suspend fun initializeMap(map: MapLibreMap): MapLibreMap {
        return try {
            Log.d(TAG, "?? Starting map initialization")
            mapState.mapLibreMap = map
            setupMapStyle(map)
            setupInitialPosition(map)
            setupGestures(map)
            setupListeners(map)
            // CRITICAL FIX: Set map instance in TaskManagerCoordinator for cleanup operations
            taskManager.setMapInstance(map)
            Log.d(TAG, "?? Set map instance in TaskManagerCoordinator for task switching cleanup")
            Log.d(TAG, "? Map initialization completed successfully")
            map
        } catch (e: Exception) {
            Log.e(TAG, "? Fatal error in map initialization: ${e.message}", e)
            map
        }
    }

    private fun setupMapStyle(map: MapLibreMap) {
        val styleName = mapStateStore.mapStyleName.value
        val styleUrl = getMapStyleUrl(styleName)
        map.setStyle(styleUrl) { _ ->
            Log.d(TAG, "Map style loaded: $styleName")

            // Initialize overlays after style is loaded
            setupOverlays(map)

            loadMapData(map)
        }
    }

    private fun setupInitialPosition(map: MapLibreMap) {
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                org.maplibre.android.geometry.LatLng(INITIAL_LATITUDE, INITIAL_LONGITUDE),
                INITIAL_ZOOM
            )
        )
        // Keep Compose overlays (distance circles) in sync from the first frame
        stateActions.updateCurrentZoom(INITIAL_ZOOM.toFloat())
        Log.d(TAG, "Initial map position set")
    }

    private fun loadMapData(map: MapLibreMap) {
        try {
            // Load airspace data
            loadAndApplyAirspace(context, map)

            // Load waypoints
            val (waypointFiles, waypointChecks) = loadWaypointFiles(context)
            loadAndApplyWaypoints(context, map, waypointFiles, waypointChecks)

            // Plot saved task if available
            plotSavedTask(map)
            mapState.blueLocationOverlay?.bringToFront()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading map data: ${e.message}", e)
        }
    }

    private fun plotSavedTask(map: MapLibreMap) {
        try {
            if (taskManager.currentTask.waypoints.isNotEmpty()) {
                Log.d(TAG, "🎯 Plotting saved task with ${taskManager.currentTask.waypoints.size} waypoints")
                taskManager.plotOnMap(map)
            } else {
                Log.d(TAG, "🎯 No saved task to plot")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error plotting saved task: ${e.message}", e)
        }
    }

    private fun setupOverlays(map: MapLibreMap) {
        try {
            // Initialize blue location overlay
            mapState.blueLocationOverlay = BlueLocationOverlay(context, map)
            mapState.blueLocationOverlay?.initialize()
            Log.d(TAG, "🔵 Blue location overlay initialized")

            // DISABLED: Map-based distance circles replaced with DistanceCirclesCanvas
            // The circles are now drawn as a fixed screen overlay in MapScreen.kt
            // This prevents them from moving with the map
            // mapState.distanceCirclesOverlay = DistanceCirclesOverlay(context, map)
            // mapState.distanceCirclesOverlay?.initialize()
            // mapState.distanceCirclesOverlay?.setVisible(mapState.showDistanceCircles)
            Log.d(TAG, "⭕ Distance circles using Canvas overlay (map-based circles disabled)")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up overlays: ${e.message}", e)
        }
    }

    private fun setupGestures(map: MapLibreMap) {
        // Disable MapLibre standard gestures for custom gesture system
        map.uiSettings.isZoomGesturesEnabled = false
        map.uiSettings.isRotateGesturesEnabled = false
        map.uiSettings.isTiltGesturesEnabled = false
        map.uiSettings.isScrollGesturesEnabled = false
        map.uiSettings.isQuickZoomGesturesEnabled = false
        Log.d(TAG, "✅ MapLibre standard gestures disabled for custom system")
    }

    private fun setupListeners(map: MapLibreMap) {
        // Move listener for pan detection
        map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) {
                Log.d(TAG, "🖐️ Map movement detected - fingers: ${detector.pointersCount}")
                handleMapMovement(map)
            }

            override fun onMove(detector: MoveGestureDetector) {
                if (detector.pointersCount >= 2) {
                    Log.d(TAG, "Two-finger pan in progress")
                }
            }

            override fun onMoveEnd(detector: MoveGestureDetector) {
                Log.d(TAG, "Pan ended")
                refreshWaypoints(map)
            }
        })

        // Rotation listener for orientation override
        map.addOnRotateListener(object : MapLibreMap.OnRotateListener {
            override fun onRotateBegin(detector: org.maplibre.android.gestures.RotateGestureDetector) {
                Log.d(TAG, "🔄 Map rotation started")
                orientationManager.onUserInteraction()
            }

            override fun onRotate(detector: org.maplibre.android.gestures.RotateGestureDetector) {
                // Rotation in progress
            }

            override fun onRotateEnd(detector: org.maplibre.android.gestures.RotateGestureDetector) {
                Log.d(TAG, "🔄 Map rotation ended")
            }
        })

        // Camera change listener for zoom-adaptive distance circles
        map.addOnCameraIdleListener {
            try {
                val currentZoom = map.cameraPosition.zoom
                stateActions.updateCurrentZoom(currentZoom.toFloat())
                // Canvas overlay listens to MapStateStore.currentZoom for zoom-adaptive effects.
                Log.d(TAG, "Camera idle, zoom: $currentZoom")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating distance circles zoom: ${e.message}", e)
            }
        }
    }

    private fun handleMapMovement(map: MapLibreMap) {
        // Trigger orientation override on user pan gesture
        orientationManager.onUserInteraction()

        // Save current position before movement for return functionality
        if (!mapStateStore.showReturnButton.value) {
            val currentLocation = unifiedSensorManager.gpsFlow.value
            if (currentLocation != null) {
                stateActions.saveLocation(
                    location = MapStateStore.MapPoint(
                        latitude = currentLocation.latLng.latitude,
                        longitude = currentLocation.latLng.longitude
                    ),
                    zoom = map.cameraPosition.zoom,
                    bearing = map.cameraPosition.bearing
                )
                Log.d(TAG, "Saved position for return")
            }
        }

        // Show return button on user interaction
        stateActions.setShowReturnButton(true)
        stateActions.updateLastUserPanTime(System.currentTimeMillis())
        Log.d(TAG, "?. User interaction detected - return button shown")
    }

    private fun refreshWaypoints(map: MapLibreMap) {
        try {
            val (waypointFiles, waypointChecks) = loadWaypointFiles(context)
            loadAndApplyWaypoints(context, map, waypointFiles, waypointChecks)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error refreshing waypoints: ${e.message}", e)
        }
    }

}





