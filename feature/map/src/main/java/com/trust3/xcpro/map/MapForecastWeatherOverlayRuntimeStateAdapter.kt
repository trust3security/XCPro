package com.trust3.xcpro.map

import org.maplibre.android.maps.MapLibreMap

internal class MapForecastWeatherOverlayRuntimeStateAdapter(
    private val mapState: MapScreenState
) : ForecastWeatherOverlayRuntimeState {
    override val mapLibreMap: MapLibreMap?
        get() = mapState.mapLibreMap

    override var forecastOverlay: ForecastRasterOverlay?
        get() = mapState.forecastOverlay
        set(value) {
            mapState.forecastOverlay = value
        }

    override var forecastWindOverlay: ForecastRasterOverlay?
        get() = mapState.forecastWindOverlay
        set(value) {
            mapState.forecastWindOverlay = value
        }

    override var skySightSatelliteOverlay: SkySightSatelliteOverlay?
        get() = mapState.skySightSatelliteOverlay
        set(value) {
            mapState.skySightSatelliteOverlay = value
        }

    override var weatherRainOverlay: WeatherRainOverlay?
        get() = mapState.weatherRainOverlay
        set(value) {
            mapState.weatherRainOverlay = value
        }
}
