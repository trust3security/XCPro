package com.trust3.xcpro.map.ui

import androidx.compose.ui.geometry.Rect
import com.trust3.xcpro.map.MapGestureRegion
import com.trust3.xcpro.map.MapOverlayGestureTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        MapOverlayGestureTarget.CARD_GRID -> 0
        MapOverlayGestureTarget.FLIGHT_MODE -> 1
        MapOverlayGestureTarget.SIDE_HAMBURGER,
        MapOverlayGestureTarget.SETTINGS_SHORTCUT,
        MapOverlayGestureTarget.VARIOMETER,
        MapOverlayGestureTarget.BALLAST -> 2
    }
}
