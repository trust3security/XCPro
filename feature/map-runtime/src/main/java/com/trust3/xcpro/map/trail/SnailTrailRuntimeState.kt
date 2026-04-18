package com.trust3.xcpro.map.trail

import com.trust3.xcpro.map.BlueLocationOverlay
import org.maplibre.android.maps.MapView

/**
 * Shell-held MapLibre handles needed by the trail runtime.
 */
interface SnailTrailRuntimeState {
    var mapView: MapView?
    var blueLocationOverlay: BlueLocationOverlay?
    var snailTrailOverlay: SnailTrailOverlay?
}
