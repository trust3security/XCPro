package com.example.xcpro.map

import android.content.Context
import android.util.Log
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints
import com.example.xcpro.map.trail.SnailTrailManager
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap

internal class MapOverlayManagerRuntimeBaseOpsDelegate(
    private val context: Context,
    private val mapStateReader: MapStateReader,
    private val taskRenderSyncCoordinator: TaskRenderSyncCoordinator,
    private val taskWaypointCountProvider: () -> Int,
    private val stateActions: MapStateActions,
    private val snailTrailManager: SnailTrailManager,
    private val coroutineScope: CoroutineScope,
    private val airspaceUseCase: AirspaceUseCase,
    private val waypointFilesUseCase: WaypointFilesUseCase
) {
    private var refreshAirspaceJob: Job? = null
    private val refreshAirspaceRequestId = AtomicLong(0L)

    fun toggleDistanceCircles() {
        stateActions.toggleDistanceCircles()
        val next = mapStateReader.showDistanceCircles.value
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Distance circles toggled: $next (Canvas overlay is active)")
        }
    }

    fun refreshAirspace(map: MapLibreMap?) {
        val requestId = refreshAirspaceRequestId.incrementAndGet()
        refreshAirspaceJob?.cancel()
        refreshAirspaceJob = coroutineScope.launch {
            try {
                loadAndApplyAirspace(map, airspaceUseCase)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Airspace overlays refreshed (requestId=$requestId)")
                }
            } catch (cancellation: CancellationException) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Airspace refresh canceled (requestId=$requestId)")
                }
                throw cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing airspace: ${e.message}", e)
            } finally {
                if (refreshAirspaceRequestId.get() == requestId) {
                    refreshAirspaceJob = null
                }
            }
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
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Refreshing task overlays for ${taskWaypointCountProvider()} waypoints"
                )
            }
            taskRenderSyncCoordinator.onOverlayRefresh(map)
        } catch (e: Exception) {
            Log.e(TAG, "Error plotting saved task: ${e.message}", e)
        }
    }

    fun clearTaskOverlays(map: MapLibreMap?) {
        try {
            taskRenderSyncCoordinator.clearTaskVisuals(map)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Task overlays cleared (handled by TaskManager)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing task overlays: ${e.message}", e)
        }
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

    fun onMapDetached() {
        refreshAirspaceJob?.cancel()
        refreshAirspaceJob = null
    }

    private companion object {
        private const val TAG = "MapOverlayManager"
    }
}
