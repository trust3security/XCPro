package com.example.xcpro.map.ui

import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.map.MapCommand
import com.example.xcpro.map.MapStyleUrlResolver
import com.example.xcpro.map.MapOverlayManager
import org.maplibre.android.maps.MapLibreMap

/**
 * UI-only runtime controller that applies imperative map commands.
 */
class MapRuntimeController(
    private val overlayManager: MapOverlayManager,
    private val fitCurrentTask: () -> Unit = {}
) {
    companion object {
        private const val TAG = "MapRuntimeController"
    }

    private var map: MapLibreMap? = null
    private var pendingStyleName: String? = null
    private var pendingFitCurrentTask = false
    private var mapGeneration: Long = 0L
    private var styleRequestToken: Long = 0L

    fun onMapReady(map: MapLibreMap) {
        this.map = map
        mapGeneration++
        // Replay the latest style command that arrived before map readiness.
        val queuedStyleName = pendingStyleName
        pendingStyleName = null
        if (!queuedStyleName.isNullOrBlank()) {
            applyStyle(queuedStyleName)
        }
        if (pendingFitCurrentTask) {
            applyFitCurrentTask()
        }
    }

    fun clearMap() {
        overlayManager.onMapDetached()
        map = null
        pendingStyleName = null
        pendingFitCurrentTask = false
        mapGeneration++
        styleRequestToken++
    }

    fun apply(command: MapCommand) {
        when (command) {
            is MapCommand.SetStyle -> applyStyle(command.styleName)
            MapCommand.FitCurrentTask -> applyFitCurrentTask()
        }
    }

    private fun applyStyle(styleName: String) {
        val currentMap = map ?: run {
            pendingStyleName = styleName
            return
        }
        val activeGeneration = mapGeneration
        val requestToken = ++styleRequestToken
        val styleUrl = MapStyleUrlResolver.resolve(styleName)
        try {
            currentMap.setStyle(styleUrl) {
                val isCurrentMap = map === currentMap && mapGeneration == activeGeneration
                val isCurrentRequest = styleRequestToken == requestToken
                if (!isCurrentMap || !isCurrentRequest) {
                    AppLogger.d(TAG, "Ignoring stale map style callback for $styleName")
                    return@setStyle
                }
                AppLogger.d(TAG, "Map style loaded: $styleName")
                overlayManager.onMapStyleChanged(currentMap)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to set map style: $styleName", e)
        }
    }

    private fun applyFitCurrentTask() {
        val currentMap = map ?: run {
            pendingFitCurrentTask = true
            return
        }
        try {
            pendingFitCurrentTask = false
            fitCurrentTask()
            AppLogger.d(TAG, "Fit current task applied for active map=${currentMap.hashCode()}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to fit current task", e)
        }
    }
}
