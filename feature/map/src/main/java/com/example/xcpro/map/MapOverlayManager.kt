package com.example.xcpro.map

import android.content.Context
import android.util.Log
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.clampAdsbIconSizePx
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.ogn.OGN_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.clampOgnIconSizePx
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskMapRenderRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Centralized overlay management for MapScreen. Handles distance circles, airspace, waypoints,
 * and task plotting interactions with TaskManagerCoordinator.
 */
class MapOverlayManager(
    private val context: Context,
    private val mapState: MapScreenState,
    private val mapStateReader: MapStateReader,
    private val taskManager: TaskManagerCoordinator,
    private val stateActions: MapStateActions,
    private val snailTrailManager: SnailTrailManager,
    private val coroutineScope: CoroutineScope,
    private val airspaceUseCase: AirspaceUseCase,
    private val waypointFilesUseCase: WaypointFilesUseCase
) {
    companion object {
        private const val TAG = "MapOverlayManager"
    }

    private var latestOgnTargets: List<OgnTrafficTarget> = emptyList()
    private var latestAdsbTargets: List<AdsbTrafficUiModel> = emptyList()
    private var ognIconSizePx: Int = OGN_ICON_SIZE_DEFAULT_PX
    private var adsbIconSizePx: Int = ADSB_ICON_SIZE_DEFAULT_PX

    fun toggleDistanceCircles() {
        stateActions.toggleDistanceCircles()
        val next = mapStateReader.showDistanceCircles.value
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Distance circles toggled: $next (Canvas overlay is active)"
            )
        }
    }

    fun refreshAirspace(map: MapLibreMap?) {
        try {
            coroutineScope.launch {
                loadAndApplyAirspace(map, airspaceUseCase)
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Airspace overlays refreshed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing airspace: ${e.message}", e)
        }
    }

    fun refreshWaypoints(map: MapLibreMap?) {
        try {
            coroutineScope.launch {
                val (files, checks) = waypointFilesUseCase.loadWaypointFiles()
                loadAndApplyWaypoints(context, map, files, checks)
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Waypoint overlays refreshed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing waypoints: ${e.message}", e)
        }
    }

    fun plotSavedTask(map: MapLibreMap?) {
        try {
            if (taskManager.currentTask.waypoints.isNotEmpty()) {
                if (BuildConfig.DEBUG) {
                Log.d(
                        TAG,
                        "Plotting saved task with ${taskManager.currentTask.waypoints.size} waypoints"
                    )
                }
                TaskMapRenderRouter.plotCurrentTask(taskManager, map)
            } else if (BuildConfig.DEBUG) {
                Log.d(TAG, "No saved task to plot")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error plotting saved task: ${e.message}", e)
        }
    }

    fun clearTaskOverlays(map: MapLibreMap?) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Task overlays cleared (handled by TaskManager)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing task overlays: ${e.message}", e)
        }
    }

    fun onMapStyleChanged(map: MapLibreMap?) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Map style changed, reloading overlays")
            }
            refreshAirspace(map)
            refreshWaypoints(map)
            plotSavedTask(map)
            if (mapState.blueLocationOverlay == null && map != null) {
                Log.d(TAG, "Blue location overlay missing after style change; creating now")
                mapState.blueLocationOverlay = BlueLocationOverlay(context, map)
            }
            mapState.blueLocationOverlay?.initialize()
            mapState.blueLocationOverlay?.let {
                Log.d(TAG, "Blue location overlay initialized via style change")
            }
            if (map != null) {
                mapState.ognTrafficOverlay?.cleanup()
                mapState.ognTrafficOverlay = createOgnTrafficOverlay(map)
                mapState.ognTrafficOverlay?.initialize()
                mapState.ognTrafficOverlay?.render(latestOgnTargets)

                mapState.adsbTrafficOverlay?.cleanup()
                mapState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
                mapState.adsbTrafficOverlay?.initialize()
                mapState.adsbTrafficOverlay?.render(latestAdsbTargets)
            }
            snailTrailManager.onMapStyleChanged(map)
            mapState.blueLocationOverlay?.bringToFront()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "All overlays reloaded for new style")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling map style change: ${e.message}", e)
        }
    }

    fun initializeOverlays(map: MapLibreMap?) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Initializing map overlays")
            }
            refreshAirspace(map)
            refreshWaypoints(map)
            plotSavedTask(map)
            if (map != null) {
                mapState.ognTrafficOverlay = createOgnTrafficOverlay(map)
                mapState.ognTrafficOverlay?.initialize()
                mapState.ognTrafficOverlay?.render(latestOgnTargets)

                mapState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
                mapState.adsbTrafficOverlay?.initialize()
                mapState.adsbTrafficOverlay?.render(latestAdsbTargets)
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Map overlays initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing overlays: ${e.message}", e)
        }
    }

    fun updateOgnTrafficTargets(targets: List<OgnTrafficTarget>) {
        latestOgnTargets = targets
        val map = mapState.mapLibreMap ?: return
        if (mapState.ognTrafficOverlay == null) {
            mapState.ognTrafficOverlay = createOgnTrafficOverlay(map)
        }
        mapState.ognTrafficOverlay?.render(targets)
    }

    fun updateAdsbTrafficTargets(targets: List<AdsbTrafficUiModel>) {
        latestAdsbTargets = targets
        val map = mapState.mapLibreMap ?: return
        if (mapState.adsbTrafficOverlay == null) {
            mapState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
        }
        mapState.adsbTrafficOverlay?.render(targets)
    }

    fun setAdsbIconSizePx(iconSizePx: Int) {
        val clamped = clampAdsbIconSizePx(iconSizePx)
        adsbIconSizePx = clamped
        if (mapState.adsbTrafficOverlay == null) {
            val map = mapState.mapLibreMap
            if (map != null) {
                mapState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
            }
        }
        mapState.adsbTrafficOverlay?.setIconSizePx(clamped)
    }

    fun setOgnIconSizePx(iconSizePx: Int) {
        val clamped = clampOgnIconSizePx(iconSizePx)
        ognIconSizePx = clamped
        if (mapState.ognTrafficOverlay == null) {
            val map = mapState.mapLibreMap
            if (map != null) {
                mapState.ognTrafficOverlay = createOgnTrafficOverlay(map)
            }
        }
        mapState.ognTrafficOverlay?.setIconSizePx(clamped)
    }

    private fun createOgnTrafficOverlay(map: MapLibreMap): OgnTrafficOverlay =
        OgnTrafficOverlay(
            context = context,
            map = map,
            initialIconSizePx = ognIconSizePx
        )

    private fun createAdsbTrafficOverlay(map: MapLibreMap): AdsbTrafficOverlay =
        AdsbTrafficOverlay(
            context = context,
            map = map,
            initialIconSizePx = adsbIconSizePx
        )

    fun findAdsbTargetAt(tap: LatLng): Icao24? {
        return mapState.adsbTrafficOverlay?.findTargetAt(tap)
    }

    fun onZoomChanged(map: MapLibreMap?) {
        try {
            refreshWaypoints(map)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Waypoints refreshed for zoom change")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling zoom change: ${e.message}", e)
        }
    }

    fun getOverlayStatus(): String {
        return buildString {
            append("MapOverlayManager Status:\n")
            append("- Distance Circles: ${mapStateReader.showDistanceCircles.value}\n")
            append(
                "- Distance Circles Overlay: ${
                    if (mapState.distanceCirclesOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append(
                "- Blue Location Overlay: ${
                    if (mapState.blueLocationOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append(
                "- OGN Traffic Overlay: ${
                    if (mapState.ognTrafficOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append("- OGN Targets: ${latestOgnTargets.size}\n")
            append(
                "- ADS-B Traffic Overlay: ${
                    if (mapState.adsbTrafficOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append("- ADS-B Targets: ${latestAdsbTargets.size}\n")
            append("- Task Waypoints: ${taskManager.currentTask.waypoints.size}\n")
        }
    }
}
