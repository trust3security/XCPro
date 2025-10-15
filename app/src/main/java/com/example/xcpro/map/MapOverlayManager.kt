package com.example.xcpro.map

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints
import com.example.xcpro.loadWaypointFiles
import com.example.xcpro.tasks.TaskManagerCoordinator
import org.maplibre.android.maps.MapLibreMap

/**
 * Centralized overlay management for MapScreen
 * Handles distance circles, airspace, waypoints, and task plotting coordination
 */
class MapOverlayManager(
    private val context: Context,
    private val mapState: MapScreenState,
    private val taskManager: TaskManagerCoordinator
) {
    companion object {
        private const val TAG = "MapOverlayManager"
    }

    /**
     * Toggle distance circles visibility
     */
    fun toggleDistanceCircles() {
        mapState.showDistanceCircles = !mapState.showDistanceCircles
        // DISABLED: Using Canvas overlay instead of map-based circles
        // mapState.distanceCirclesOverlay?.setVisible(mapState.showDistanceCircles)
        Log.d(TAG, "⭕ Distance circles toggled: ${mapState.showDistanceCircles} (using Canvas overlay)")
    }

    /**
     * Refresh airspace overlays on the map
     */
    fun refreshAirspace(map: MapLibreMap?) {
        try {
            loadAndApplyAirspace(context, map)
            Log.d(TAG, "🛩️ Airspace overlays refreshed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error refreshing airspace: ${e.message}", e)
        }
    }

    /**
     * Refresh waypoint overlays on the map
     */
    fun refreshWaypoints(map: MapLibreMap?) {
        try {
            val (waypointFiles, waypointChecks) = loadWaypointFiles(context)
            loadAndApplyWaypoints(context, map, waypointFiles, waypointChecks)
            Log.d(TAG, "📍 Waypoint overlays refreshed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error refreshing waypoints: ${e.message}", e)
        }
    }

    /**
     * Plot saved task overlay on the map
     */
    fun plotSavedTask(map: MapLibreMap?) {
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

    /**
     * Clear all task overlays from the map
     */
    fun clearTaskOverlays(map: MapLibreMap?) {
        try {
            // TaskManager handles clearing its own overlays
            // This is a placeholder for future task overlay clearing functionality
            Log.d(TAG, "🧹 Task overlays cleared")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing task overlays: ${e.message}", e)
        }
    }

    /**
     * Handle map style changes - reload all overlays
     */
    fun onMapStyleChanged(map: MapLibreMap?) {
        try {
            Log.d(TAG, "🎨 Map style changed, reloading overlays")

            // Reload airspace and waypoints for new map style
            refreshAirspace(map)
            refreshWaypoints(map)

            // Replot current task if it exists
            plotSavedTask(map)

            Log.d(TAG, "✅ All overlays reloaded for new map style")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling map style change: ${e.message}", e)
        }
    }

    /**
     * Initialize overlays after map style is loaded
     */
    fun initializeOverlays(map: MapLibreMap?) {
        try {
            Log.d(TAG, "🚀 Initializing map overlays")

            // Load initial airspace and waypoint data
            refreshAirspace(map)
            refreshWaypoints(map)

            // Plot any existing task
            plotSavedTask(map)

            Log.d(TAG, "✅ Map overlays initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing overlays: ${e.message}", e)
        }
    }

    /**
     * Handle double-tap zoom gesture - refresh waypoints for new zoom level
     */
    fun onZoomChanged(map: MapLibreMap?) {
        try {
            // Refresh waypoints to adapt to new zoom level
            refreshWaypoints(map)
            Log.d(TAG, "🔍 Waypoints refreshed for zoom change")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling zoom change: ${e.message}", e)
        }
    }

    /**
     * Get current overlay status for debugging
     */
    fun getOverlayStatus(): String {
        return buildString {
            append("MapOverlayManager Status:\n")
            append("- Distance Circles: ${mapState.showDistanceCircles}\n")
            append("- Distance Circles Overlay: ${if (mapState.distanceCirclesOverlay != null) "Initialized" else "Not Initialized"}\n")
            append("- Blue Location Overlay: ${if (mapState.blueLocationOverlay != null) "Initialized" else "Not Initialized"}\n")
            append("- Task Waypoints: ${taskManager.currentTask.waypoints.size}\n")
        }
    }
}