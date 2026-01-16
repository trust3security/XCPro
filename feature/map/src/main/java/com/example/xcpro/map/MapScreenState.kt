package com.example.xcpro.map

import com.example.xcpro.map.trail.SnailTrailOverlay
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.plugins.scalebar.ScaleBarPlugin
import org.maplibre.android.plugins.scalebar.ScaleBarWidget

/**
 * Runtime-only map handles owned by the UI layer.
 * UI state belongs in MapStateStore; this class caches MapLibre objects/overlays only.
 */
class MapScreenState {
    var mapLibreMap: MapLibreMap? = null
    var mapView: MapView? = null
    var blueLocationOverlay: BlueLocationOverlay? = null
    var distanceCirclesOverlay: DistanceCirclesOverlay? = null
    var snailTrailOverlay: SnailTrailOverlay? = null
    var scaleBarPlugin: ScaleBarPlugin? = null
    var scaleBarWidget: ScaleBarWidget? = null
}
