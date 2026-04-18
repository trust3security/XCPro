package com.trust3.xcpro.map

import android.content.Context
import com.trust3.xcpro.airspace.AirspaceUseCase
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.flightdata.WaypointFilesUseCase
import com.trust3.xcpro.loadAndApplyAirspace
import com.trust3.xcpro.loadAndApplyWaypoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap

internal class MapInitializerDataLoader(
    private val context: Context,
    private val mapState: MapScreenState,
    private val coroutineScope: CoroutineScope,
    private val airspaceUseCase: AirspaceUseCase,
    private val waypointFilesUseCase: WaypointFilesUseCase
) {
    companion object {
        private const val TAG = "MapInitializerDataLoader"
    }

    fun loadInitialData(map: MapLibreMap) {
        try {
            coroutineScope.launch {
                loadAndApplyAirspace(map, airspaceUseCase)
            }
            coroutineScope.launch {
                val (waypointFiles, waypointChecks) = waypointFilesUseCase.loadWaypointFiles()
                loadAndApplyWaypoints(context, map, waypointFiles, waypointChecks)
            }
            mapState.blueLocationOverlay?.bringToFront()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading map data: ${e.message}", e)
        }
    }

    fun refreshWaypoints(map: MapLibreMap) {
        try {
            coroutineScope.launch {
                val (waypointFiles, waypointChecks) = waypointFilesUseCase.loadWaypointFiles()
                loadAndApplyWaypoints(context, map, waypointFiles, waypointChecks)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error refreshing waypoints: ${e.message}", e)
        }
    }
}
