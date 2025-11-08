package com.example.xcpro.map.ui

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.xcpro.map.MapGestureRegion
import com.example.xcpro.map.MapOverlayGestureTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/** Stores and retrieves persisted widget layout data for the map overlays. */
class MapWidgetLayoutStore(
    private val prefs: SharedPreferences,
    private val logger: (String) -> Unit = { Log.d(TAG, it) }
) {
    fun loadPositions(
        screenWidthPx: Float,
        screenHeightPx: Float,
        density: Density
    ): WidgetPositions {
        val hamburgerOffset = Offset(
            prefs.getFloat("side_hamburger_x", HAMBURGER_DEFAULT_X),
            prefs.getFloat("side_hamburger_y", hamburgerDefaultY(screenHeightPx, density)).coerceAtLeast(0f)
        )
        val flightModeOffset = Offset(
            prefs.getFloat("flight_mode_menu_x", FLIGHT_MODE_DEFAULT_X),
            prefs.getFloat("flight_mode_menu_y", flightModeDefaultY(density))
        )

        return WidgetPositions(
            sideHamburgerOffset = hamburgerOffset,
            flightModeOffset = flightModeOffset
        )
    }

    fun savePosition(key: String, offset: Offset) {
        with(prefs.edit()) {
            putFloat("${key}_x", offset.x)
            putFloat("${key}_y", offset.y)
            apply()
        }
        logger("$key position saved: x=${offset.x}, y=${offset.y}")
    }

    private fun hamburgerDefaultY(screenHeightPx: Float, density: Density): Float =
        max(0f, screenHeightPx / 2f - density.run { 32.dp.toPx() })

    private fun flightModeDefaultY(density: Density): Float = density.run { 80.dp.toPx() }

    companion object {
        private const val TAG = "MapWidgetLayoutStore"
        private const val HAMBURGER_DEFAULT_X = 16f
        private const val FLIGHT_MODE_DEFAULT_X = 16f
    }
}

/** Maintains gesture regions published by overlay widgets. */
class MapGestureRegistry {
    private val registry = linkedMapOf<MapOverlayGestureTarget, MapGestureRegion>()
    private val _regions = MutableStateFlow<List<MapGestureRegion>>(emptyList())
    val regions: StateFlow<List<MapGestureRegion>> = _regions.asStateFlow()

    fun update(target: MapOverlayGestureTarget, bounds: Rect, consume: Boolean) {
        registry[target] = MapGestureRegion(target, bounds, consume)
        publish()
    }

    fun clear(target: MapOverlayGestureTarget) {
        registry.remove(target)
        publish()
    }

    private fun publish() {
        _regions.value = registry.values
            .sortedWith(compareBy<MapGestureRegion> { priority(it.target) }
                .thenBy { it.bounds.width * it.bounds.height })
    }

    private fun priority(target: MapOverlayGestureTarget): Int = when (target) {
        MapOverlayGestureTarget.FLIGHT_MODE -> 0
        MapOverlayGestureTarget.SIDE_HAMBURGER, MapOverlayGestureTarget.VARIOMETER -> 1
    }
}

/** Aggregated widget offsets and sizes used by the manager + UI layer. */
data class WidgetPositions(
    val sideHamburgerOffset: Offset,
    val flightModeOffset: Offset
)
