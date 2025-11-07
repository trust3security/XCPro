package com.example.xcpro.screens.overlays

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate

// Helper function to get default flight data template for a given mode
fun getDefaultTemplateForMode(mode: FlightModeSelection, templates: List<FlightTemplate>): FlightTemplate? {
    return when (mode) {
        FlightModeSelection.CRUISE -> templates.find { it.id == "id01" }
            ?: templates.find { it.id == "essential" }
        FlightModeSelection.THERMAL -> templates.find { it.id == "id02" }
            ?: templates.find { it.id == "thermal" }
        FlightModeSelection.FINAL_GLIDE -> templates.find { it.id == "id03" }
            ?: templates.find { it.id == "final_glide" }
        FlightModeSelection.HAWK -> templates.find { it.id == "hawk" }
            ?: templates.find { it.id == "vario" }
            ?: templates.find { it.id == "id01" }
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
    return when (style) {
        "Topo" -> "https://api.maptiler.com/maps/topo/style.json?key=nYDScLfnBm52GAc3jXEZ"
        "Satellite" -> "https://api.maptiler.com/maps/hybrid/style.json?key=nYDScLfnBm52GAc3jXEZ"
        "Terrain" -> "https://api.maptiler.com/maps/outdoor/style.json?key=nYDScLfnBm52GAc3jXEZ"
        else -> "https://api.maptiler.com/maps/topo/style.json?key=nYDScLfnBm52GAc3jXEZ"
    }
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