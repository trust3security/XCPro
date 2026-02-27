package com.example.xcpro.map.ui

import android.util.Log
import com.example.xcpro.map.MapCommand
import com.example.xcpro.map.MapStyleUrlResolver
import com.example.xcpro.map.MapOverlayManager
import org.maplibre.android.maps.MapLibreMap

/**
 * UI-only runtime controller that applies imperative map commands.
 */
class MapRuntimeController(
    private val overlayManager: MapOverlayManager
) {
    companion object {
        private const val TAG = "MapRuntimeController"
    }

    private var map: MapLibreMap? = null
    private var pendingStyleName: String? = null
    private var mapGeneration: Long = 0L
    private var styleRequestToken: Long = 0L

    fun onMapReady(map: MapLibreMap) {
        this.map = map
        mapGeneration++
        // Initial style is owned by MapInitializer. Drop any stale pre-ready command
        // to avoid double style loads during cold start.
        pendingStyleName = null
    }

    fun clearMap() {
        overlayManager.onMapDetached()
        map = null
        pendingStyleName = null
        mapGeneration++
        styleRequestToken++
    }

    fun apply(command: MapCommand) {
        when (command) {
            is MapCommand.SetStyle -> applyStyle(command.styleName)
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
                    if (com.example.xcpro.map.BuildConfig.DEBUG) {
                        Log.d(TAG, "Ignoring stale map style callback for $styleName")
                    }
                    return@setStyle
                }
                Log.d(TAG, "Map style loaded: $styleName")
                overlayManager.onMapStyleChanged(currentMap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set map style: $styleName", e)
        }
    }
}
