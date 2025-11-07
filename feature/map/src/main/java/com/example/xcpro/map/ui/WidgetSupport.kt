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
        val variometerDefaults = legacyVariometerDefaults(screenHeightPx, density)
        val levoOffset = readOffset("uilevo", variometerDefaults.offset)
        val levoSize = prefs.getFloat("uilevo_size", variometerDefaults.size)

        val hamburgerOffset = Offset(
            prefs.getFloat("side_hamburger_x", HAMBURGER_DEFAULT_X),
            prefs.getFloat("side_hamburger_y", hamburgerDefaultY(screenHeightPx, density)).coerceAtLeast(0f)
        )
        val flightModeOffset = Offset(
            prefs.getFloat("flight_mode_menu_x", FLIGHT_MODE_DEFAULT_X),
            prefs.getFloat("flight_mode_menu_y", flightModeDefaultY(density))
        )

        return WidgetPositions(
            variometerOffset = levoOffset,
            variometerSizePx = levoSize,
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

    fun saveSize(key: String, size: Float) {
        with(prefs.edit()) {
            putFloat("${key}_size", size)
            apply()
        }
        logger("$key size saved: $size")
    }

    private fun readOffset(key: String, fallback: Offset): Offset = Offset(
        prefs.getFloat("${key}_x", fallback.x),
        prefs.getFloat("${key}_y", fallback.y)
    )

    private fun legacyVariometerDefaults(screenHeightPx: Float, density: Density): LegacyVariometerDefaults {
        val offset = Offset(
            prefs.getFloat("variometer_x", 20f),
            prefs.getFloat("variometer_y", screenHeightPx - 400f)
        )
        val size = prefs.getFloat(
            "variometer_size",
            density.run { 150.dp.toPx() }
        )
        return LegacyVariometerDefaults(offset, size)
    }

    private fun hamburgerDefaultY(screenHeightPx: Float, density: Density): Float =
        max(0f, screenHeightPx / 2f - density.run { 32.dp.toPx() })

    private fun flightModeDefaultY(density: Density): Float = density.run { 80.dp.toPx() }

    private data class LegacyVariometerDefaults(val offset: Offset, val size: Float)

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
    val variometerOffset: Offset,
    val variometerSizePx: Float,
    val sideHamburgerOffset: Offset,
    val flightModeOffset: Offset
)
