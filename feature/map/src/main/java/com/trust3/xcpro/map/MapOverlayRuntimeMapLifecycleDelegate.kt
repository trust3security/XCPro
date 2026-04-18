package com.trust3.xcpro.map

import android.content.Context
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.map.trail.SnailTrailManager
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
) : MapOverlayRuntimeLifecyclePort {
    companion object {
        private const val LOG_TAG = "MapOverlayManager"
    }

    override fun toggleDistanceCircles() = baseOpsDelegate.toggleDistanceCircles()
    override fun refreshAirspace(map: MapLibreMap?) = baseOpsDelegate.refreshAirspace(map)
    override fun refreshWaypoints(map: MapLibreMap?) = baseOpsDelegate.refreshWaypoints(map)
    override fun plotSavedTask(map: MapLibreMap?) = baseOpsDelegate.plotSavedTask(map)
    override fun clearTaskOverlays(map: MapLibreMap?) = baseOpsDelegate.clearTaskOverlays(map)
    override fun onZoomChanged(map: MapLibreMap?) = baseOpsDelegate.onZoomChanged(map)

    override fun onMapStyleChanged(map: MapLibreMap?) {
        try {
            refreshAirspace(map)
            refreshWaypoints(map)
            taskRenderSyncCoordinator.onMapStyleChanged(map)
            if (mapState.blueLocationOverlay == null && map != null) {
                mapState.blueLocationOverlay = BlueLocationOverlay(context, map)
            }
            mapState.blueLocationOverlay?.initialize()
            if (map != null) {
                initializeTrafficOverlaysFn(map)
                forecastOnMapStyleChanged(map)
            }
            snailTrailManager.onMapStyleChanged(map)
            bringTrafficOverlaysToFront()
        } catch (e: Exception) {
            AppLogger.e(LOG_TAG, "Error handling map style change: ${e.message}", e)
        }
    }

    override fun initializeOverlays(map: MapLibreMap?) {
        try {
            refreshAirspace(map)
            refreshWaypoints(map)
            taskRenderSyncCoordinator.onOverlayRefresh(map)
            if (map != null) {
                initializeTrafficOverlaysFn(map)
                forecastOnInitialize(map)
            }
        } catch (e: Exception) {
            AppLogger.e(LOG_TAG, "Error initializing overlays: ${e.message}", e)
        }
    }

    override fun initializeTrafficOverlays(map: MapLibreMap?) {
        initializeTrafficOverlaysFn(map)
    }

    override fun onMapDetached() {
        baseOpsDelegate.onMapDetached()
    }
}
