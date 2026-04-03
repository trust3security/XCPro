package com.example.xcpro.map

import com.example.xcpro.map.trail.SnailTrailOverlay
import com.example.xcpro.map.trail.SnailTrailRuntimeState
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.plugins.scalebar.ScaleBarPlugin
import org.maplibre.android.plugins.scalebar.ScaleBarWidget

/**
 * Runtime-only map handles owned by the UI layer.
 * UI state belongs in MapStateStore; this class caches MapLibre objects/overlays only.
 */
class MapScreenState : MapScaleBarRuntimeState, SnailTrailRuntimeState {
    var mapLibreMap: MapLibreMap? = null
    override var mapView: MapView? = null
    override var blueLocationOverlay: BlueLocationOverlay? = null
    var ognTrafficOverlay: OgnTrafficOverlayHandle? = null
    var ognTargetRingOverlay: OgnTargetRingOverlayHandle? = null
    var ognTargetLineOverlay: OgnTargetLineOverlayHandle? = null
    var ognOwnshipTargetBadgeOverlay: OgnOwnshipTargetBadgeOverlayHandle? = null
    var ognThermalOverlay: OgnThermalOverlayHandle? = null
    var ognGliderTrailOverlay: OgnGliderTrailOverlayHandle? = null
    var ognSelectedThermalOverlay: OgnSelectedThermalOverlayHandle? = null
    var adsbTrafficOverlay: AdsbTrafficOverlayHandle? = null
    var forecastOverlay: ForecastRasterOverlay? = null
    var forecastWindOverlay: ForecastRasterOverlay? = null
    var skySightSatelliteOverlay: SkySightSatelliteOverlay? = null
    var weatherRainOverlay: WeatherRainOverlay? = null
    override var snailTrailOverlay: SnailTrailOverlay? = null
    override var scaleBarPlugin: ScaleBarPlugin? = null
    override var scaleBarWidget: ScaleBarWidget? = null
    internal var scaleBarController: MapScaleBarController? = null
}
