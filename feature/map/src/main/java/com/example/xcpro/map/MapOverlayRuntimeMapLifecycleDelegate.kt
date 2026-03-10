package com.example.xcpro.map

import android.content.Context
import android.util.Log
import com.example.xcpro.map.trail.SnailTrailManager
import org.maplibre.android.maps.MapLibreMap

internal class MapOverlayRuntimeMapLifecycleDelegate(
    private val context: Context,
    private val mapState: MapScreenState,
    private val baseOpsDelegate: MapOverlayManagerRuntimeBaseOpsDelegate,
    private val taskRenderSyncCoordinator: TaskRenderSyncCoordinator,
    private val initializeTrafficOverlaysFn: (MapLibreMap?) -> Unit,
    private val forecastOnMapStyleChanged: (MapLibreMap?) -> Unit,
    private val forecastOnInitialize: (MapLibreMap?) -> Unit,
    private val bringTrafficOverlaysToFront: () -> Unit,
    private val snailTrailManager: SnailTrailManager
) {
    fun toggleDistanceCircles() = baseOpsDelegate.toggleDistanceCircles()
    fun refreshAirspace(map: MapLibreMap?) = baseOpsDelegate.refreshAirspace(map)
    fun refreshWaypoints(map: MapLibreMap?) = baseOpsDelegate.refreshWaypoints(map)
    fun plotSavedTask(map: MapLibreMap?) = baseOpsDelegate.plotSavedTask(map)
    fun clearTaskOverlays(map: MapLibreMap?) = baseOpsDelegate.clearTaskOverlays(map)
    fun onZoomChanged(map: MapLibreMap?) = baseOpsDelegate.onZoomChanged(map)

    fun onMapStyleChanged(map: MapLibreMap?) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d("MapOverlayManager", "Map style changed, reloading overlays")
            }
            refreshAirspace(map)
            refreshWaypoints(map)
            taskRenderSyncCoordinator.onMapStyleChanged(map)
            if (mapState.blueLocationOverlay == null && map != null) {
                mapState.blueLocationOverlay = BlueLocationOverlay(context, map)
            }
            mapState.blueLocationOverlay?.initialize()
            mapState.blueLocationOverlay?.let {
                if (BuildConfig.DEBUG) {
                    Log.d("MapOverlayManager", "Blue location overlay initialized via style change")
                }
            }
            if (map != null) {
                initializeTrafficOverlaysFn(map)
                forecastOnMapStyleChanged(map)
            }
            snailTrailManager.onMapStyleChanged(map)
            bringTrafficOverlaysToFront()
            if (BuildConfig.DEBUG) {
                Log.d("MapOverlayManager", "All overlays reloaded for new style")
            }
        } catch (e: Exception) {
            Log.e("MapOverlayManager", "Error handling map style change: ${e.message}", e)
        }
    }

    fun initializeOverlays(map: MapLibreMap?) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d("MapOverlayManager", "Initializing map overlays")
            }
            refreshAirspace(map)
            refreshWaypoints(map)
            taskRenderSyncCoordinator.onOverlayRefresh(map)
            if (map != null) {
                initializeTrafficOverlaysFn(map)
                forecastOnInitialize(map)
            }
            if (BuildConfig.DEBUG) {
                Log.d("MapOverlayManager", "Map overlays initialized successfully")
            }
        } catch (e: Exception) {
            Log.e("MapOverlayManager", "Error initializing overlays: ${e.message}", e)
        }
    }

    fun initializeTrafficOverlays(map: MapLibreMap?) {
        initializeTrafficOverlaysFn(map)
    }
}
