package com.example.xcpro.map.ui.widgets

import android.util.Log
import androidx.compose.ui.geometry.Rect
import com.example.xcpro.map.MapGestureRegion
import com.example.xcpro.map.MapOverlayGestureTarget
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.ui.MapGestureRegistry
import kotlinx.coroutines.flow.StateFlow

/**
 * Centralized UI Widget Management for MapScreen
 * Handles draggable components, edit mode, and widget positioning/sizing
 */
class MapUIWidgetManager(
    internal val mapState: MapScreenState
) {
    companion object {
        private const val TAG = "MapUIWidgetManager"
    }

    private val gestureRegistry = MapGestureRegistry()
    val gestureRegions: StateFlow<List<MapGestureRegion>> = gestureRegistry.regions

    /**
     * Register or update a gesture region so the map can short-circuit pointer handling.
     *
     * AI-NOTE: Gesture pass-through relies on screen-space bounds; we update them whenever the
     * overlay recomposes to keep the map gesture layer in sync.
     */
    fun updateGestureRegion(
        target: MapOverlayGestureTarget,
        bounds: Rect,
        consumeGestures: Boolean = true
    ) {
        gestureRegistry.update(target, bounds, consumeGestures)
        Log.d(TAG, "Updated gesture region for $target: $bounds (consume=$consumeGestures)")
    }

    /**
     * Clear a gesture region when its overlay leaves composition.
     */
    fun clearGestureRegion(target: MapOverlayGestureTarget) {
        gestureRegistry.clear(target)
        Log.d(TAG, "Cleared gesture region for $target")
    }
}
