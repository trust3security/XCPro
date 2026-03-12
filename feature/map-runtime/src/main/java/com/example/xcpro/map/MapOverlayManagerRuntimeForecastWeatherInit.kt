package com.example.xcpro.map

import org.maplibre.android.maps.MapLibreMap

internal fun applyForecastWeatherStyleChange(
    runtimeState: ForecastWeatherOverlayRuntimeState,
    map: MapLibreMap,
    reapplyForecastOverlay: (MapLibreMap) -> Unit,
    reapplySkySightSatelliteOverlay: (MapLibreMap) -> Unit,
    reapplyWeatherRainOverlay: (MapLibreMap) -> Unit
) {
    runtimeState.forecastOverlay?.cleanup()
    runtimeState.forecastWindOverlay?.cleanup()
    runtimeState.forecastOverlay = ForecastRasterOverlay(
        map = map,
        idNamespace = "primary"
    )
    runtimeState.forecastWindOverlay = ForecastRasterOverlay(
        map = map,
        idNamespace = "wind"
    )
    reapplyForecastOverlay(map)

    runtimeState.skySightSatelliteOverlay?.cleanup()
    runtimeState.skySightSatelliteOverlay = SkySightSatelliteOverlay(map)
    reapplySkySightSatelliteOverlay(map)

    runtimeState.weatherRainOverlay?.cleanup()
    runtimeState.weatherRainOverlay = WeatherRainOverlay(map)
    reapplyWeatherRainOverlay(map)
}

internal fun initializeForecastWeatherOverlays(
    runtimeState: ForecastWeatherOverlayRuntimeState,
    map: MapLibreMap,
    reapplyForecastOverlay: (MapLibreMap) -> Unit,
    reapplySkySightSatelliteOverlay: (MapLibreMap) -> Unit,
    reapplyWeatherRainOverlay: (MapLibreMap) -> Unit
) {
    runtimeState.forecastOverlay = ForecastRasterOverlay(
        map = map,
        idNamespace = "primary"
    )
    runtimeState.forecastWindOverlay = ForecastRasterOverlay(
        map = map,
        idNamespace = "wind"
    )
    reapplyForecastOverlay(map)

    runtimeState.skySightSatelliteOverlay = SkySightSatelliteOverlay(map)
    reapplySkySightSatelliteOverlay(map)

    runtimeState.weatherRainOverlay = WeatherRainOverlay(map)
    reapplyWeatherRainOverlay(map)
}
