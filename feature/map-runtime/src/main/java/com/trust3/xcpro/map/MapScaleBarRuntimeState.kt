package com.trust3.xcpro.map

import org.maplibre.android.maps.MapView
import org.maplibre.android.plugins.scalebar.ScaleBarPlugin
import org.maplibre.android.plugins.scalebar.ScaleBarWidget

interface MapScaleBarRuntimeState {
    var mapView: MapView?
    var scaleBarPlugin: ScaleBarPlugin?
    var scaleBarWidget: ScaleBarWidget?
}
