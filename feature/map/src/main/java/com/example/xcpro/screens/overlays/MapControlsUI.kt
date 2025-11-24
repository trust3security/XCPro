package com.example.xcpro.screens.overlays

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.xcpro.map.BuildConfig

// Helper function to get default flight data template for a given mode
fun getDefaultTemplateForMode(mode: FlightModeSelection, templates: List<FlightTemplate>): FlightTemplate? {
    return when (mode) {
        FlightModeSelection.CRUISE -> templates.find { it.id == "id01" }
            ?: templates.find { it.id == "essential" }
        FlightModeSelection.THERMAL -> templates.find { it.id == "id02" }
            ?: templates.find { it.id == "thermal" }
        FlightModeSelection.FINAL_GLIDE -> templates.find { it.id == "id03" }
            ?: templates.find { it.id == "final_glide" }
    }
}

// Helper function to detect two-finger drag gestures
// Note: This is a simplified version for the refactored components
fun detectTwoFingerDrag(onDrag: (dx: Float, dy: Float) -> Unit) {
    // Simplified implementation - to be used with appropriate gesture handling
    android.util.Log.d("MapControls", "Two-finger drag gesture detected")
    // Implementation details should be handled in the specific usage context
}

// Helper function to get map style URL based on style name
fun getMapStyleUrl(style: String): String {
    val key = BuildConfig.MAPLIBRE_API_KEY
    val useDemoTiles = key.isNullOrBlank()

    if (useDemoTiles) {
        // No key provided: use open MapLibre demo style to avoid crashes
        return "https://demotiles.maplibre.org/style.json"
    }

    val mapId = when (style) {
        "Topo" -> "topo"
        "Satellite" -> "hybrid"
        "Terrain" -> "outdoor"
        else -> "topo"
    }
    return "https://api.maptiler.com/maps/$mapId/style.json?key=$key"
}

// Map style constants
object MapStyles {
    const val TOPO = "Topo"
    const val SATELLITE = "Satellite"
    const val TERRAIN = "Terrain"

    val ALL_STYLES = listOf(TOPO, SATELLITE, TERRAIN)
}

// Map interaction constants
object MapInteraction {
    const val SWIPE_COOLDOWN_MS = 300L
    const val ZOOM_SENSITIVITY = 0.005f
    const val DOUBLE_TAP_ZOOM_DELTA = 1.0
    const val FONT_SCALE_FACTOR = 0.08f

    // Default map location (can be customized)
    const val INITIAL_LATITUDE = 47.6062
    const val INITIAL_LONGITUDE = -122.3321
    const val INITIAL_ZOOM = 10.0
}
