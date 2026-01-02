package com.example.xcpro.map.ui

import android.util.Log
import com.example.xcpro.map.MapCommand
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.screens.overlays.getMapStyleUrl
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

    fun onMapReady(map: MapLibreMap) {
        this.map = map
        pendingStyleName?.let { styleName ->
            pendingStyleName = null
            applyStyle(styleName)
        }
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
        val styleUrl = getMapStyleUrl(styleName)
        try {
            currentMap.setStyle(styleUrl) {
                Log.d(TAG, "Map style loaded: $styleName ($styleUrl)")
                overlayManager.onMapStyleChanged(currentMap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set style: ${e.message}", e)
        }
    }
}
