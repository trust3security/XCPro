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
    var ognTrafficOverlay: OgnTrafficOverlayHandle? = null
    var ognTargetRingOverlay: OgnTargetRingOverlayHandle? = null
    var ognTargetLineOverlay: OgnTargetLineOverlayHandle? = null
    var ognThermalOverlay: OgnThermalOverlayHandle? = null
    var ognGliderTrailOverlay: OgnGliderTrailOverlayHandle? = null
    var adsbTrafficOverlay: AdsbTrafficOverlayHandle? = null
    var forecastOverlay: ForecastRasterOverlay? = null
    var forecastWindOverlay: ForecastRasterOverlay? = null
    var skySightSatelliteOverlay: SkySightSatelliteOverlay? = null
    var weatherRainOverlay: WeatherRainOverlay? = null
    var snailTrailOverlay: SnailTrailOverlay? = null
    var scaleBarPlugin: ScaleBarPlugin? = null
    var scaleBarWidget: ScaleBarWidget? = null
    internal var scaleBarController: MapScaleBarController? = null
}
