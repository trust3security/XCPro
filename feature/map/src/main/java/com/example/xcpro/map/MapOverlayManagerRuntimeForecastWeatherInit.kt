package com.example.xcpro.map

import org.maplibre.android.maps.MapLibreMap

internal fun applyForecastWeatherStyleChange(
    mapState: MapScreenState,
    map: MapLibreMap,
    reapplyForecastOverlay: (MapLibreMap) -> Unit,
    reapplySkySightSatelliteOverlay: (MapLibreMap) -> Unit,
    reapplyWeatherRainOverlay: (MapLibreMap) -> Unit
) {
    mapState.forecastOverlay?.cleanup()
    mapState.forecastWindOverlay?.cleanup()
    mapState.forecastOverlay = ForecastRasterOverlay(
        map = map,
        idNamespace = "primary"
    )
    mapState.forecastWindOverlay = ForecastRasterOverlay(
        map = map,
        idNamespace = "wind"
    )
    reapplyForecastOverlay(map)

    mapState.skySightSatelliteOverlay?.cleanup()
    mapState.skySightSatelliteOverlay = SkySightSatelliteOverlay(map)
    reapplySkySightSatelliteOverlay(map)

    mapState.weatherRainOverlay?.cleanup()
    mapState.weatherRainOverlay = WeatherRainOverlay(map)
    reapplyWeatherRainOverlay(map)
}

internal fun initializeForecastWeatherOverlays(
    mapState: MapScreenState,
    map: MapLibreMap,
    reapplyForecastOverlay: (MapLibreMap) -> Unit,
    reapplySkySightSatelliteOverlay: (MapLibreMap) -> Unit,
    reapplyWeatherRainOverlay: (MapLibreMap) -> Unit
) {
    mapState.forecastOverlay = ForecastRasterOverlay(
        map = map,
        idNamespace = "primary"
    )
    mapState.forecastWindOverlay = ForecastRasterOverlay(
        map = map,
        idNamespace = "wind"
    )
    reapplyForecastOverlay(map)

    mapState.skySightSatelliteOverlay = SkySightSatelliteOverlay(map)
    reapplySkySightSatelliteOverlay(map)

    mapState.weatherRainOverlay = WeatherRainOverlay(map)
    reapplyWeatherRainOverlay(map)
}
