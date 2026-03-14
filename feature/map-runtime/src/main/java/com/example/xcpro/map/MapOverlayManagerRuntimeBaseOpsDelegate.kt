package com.example.xcpro.map

import com.example.xcpro.core.common.logging.AppLogger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap

class MapOverlayManagerRuntimeBaseOpsDelegate(
    private val mapStateReader: MapStateReader,
    private val taskRenderSyncCoordinator: TaskRenderSyncCoordinator,
    private val taskWaypointCountProvider: () -> Int,
    private val stateActions: MapStateActions,
    private val coroutineScope: CoroutineScope,
    private val refreshAirspaceFn: suspend (MapLibreMap?) -> Unit,
    private val refreshWaypointsFn: suspend (MapLibreMap?) -> Unit
) {
    private var refreshAirspaceJob: Job? = null
    private val refreshAirspaceRequestId = AtomicLong(0L)

    fun toggleDistanceCircles() {
        stateActions.toggleDistanceCircles()
    }

    fun refreshAirspace(map: MapLibreMap?) {
        val requestId = refreshAirspaceRequestId.incrementAndGet()
        refreshAirspaceJob?.cancel()
        refreshAirspaceJob = coroutineScope.launch {
            try {
                refreshAirspaceFn(map)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                AppLogger.e(LOG_TAG, "Error refreshing airspace (requestId=$requestId): ${e.message}", e)
            } finally {
                if (refreshAirspaceRequestId.get() == requestId) {
                    refreshAirspaceJob = null
                }
            }
        }
    }

    fun refreshWaypoints(map: MapLibreMap?) {
        coroutineScope.launch {
            try {
                refreshWaypointsFn(map)
            } catch (e: Exception) {
                AppLogger.e(LOG_TAG, "Error refreshing waypoints: ${e.message}", e)
            }
        }
    }

    fun plotSavedTask(map: MapLibreMap?) {
        try {
            taskRenderSyncCoordinator.onOverlayRefresh(map)
        } catch (e: Exception) {
            AppLogger.e(
                LOG_TAG,
                "Error plotting saved task for ${taskWaypointCountProvider()} waypoints: ${e.message}",
                e
            )
        }
    }

    fun clearTaskOverlays(map: MapLibreMap?) {
        try {
            taskRenderSyncCoordinator.clearTaskVisuals(map)
        } catch (e: Exception) {
            AppLogger.e(LOG_TAG, "Error clearing task overlays: ${e.message}", e)
        }
    }

    fun onZoomChanged(map: MapLibreMap?) {
        try {
            refreshWaypoints(map)
        } catch (e: Exception) {
            AppLogger.e(LOG_TAG, "Error handling zoom change: ${e.message}", e)
        }
    }

    fun onMapDetached() {
        refreshAirspaceJob?.cancel()
        refreshAirspaceJob = null
    }

    private companion object {
        private const val LOG_TAG = "MapOverlayManager"
    }
}
