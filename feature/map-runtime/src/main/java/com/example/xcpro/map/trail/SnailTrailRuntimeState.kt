package com.example.xcpro.map.trail

import com.example.xcpro.map.BlueLocationOverlay
import org.maplibre.android.maps.MapView

/**
 * Shell-held MapLibre handles needed by the trail runtime.
 */
interface SnailTrailRuntimeState {
    var mapView: MapView?
    var blueLocationOverlay: BlueLocationOverlay?
    var snailTrailOverlay: SnailTrailOverlay?
}
