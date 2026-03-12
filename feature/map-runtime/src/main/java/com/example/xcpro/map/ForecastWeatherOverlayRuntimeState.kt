package com.example.xcpro.map

import org.maplibre.android.maps.MapLibreMap

interface ForecastWeatherOverlayRuntimeState {
    val mapLibreMap: MapLibreMap?
    var forecastOverlay: ForecastRasterOverlay?
    var forecastWindOverlay: ForecastRasterOverlay?
    var skySightSatelliteOverlay: SkySightSatelliteOverlay?
    var weatherRainOverlay: WeatherRainOverlay?
}
