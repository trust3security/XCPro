package com.example.xcpro.map

import com.example.xcpro.weather.rain.WeatherRadarStatusCode

internal data class MapOverlayForecastWeatherStatus(
    val forecastOverlayEnabled: Boolean,
    val forecastWindOverlayEnabled: Boolean,
    val satelliteContrastIconsEnabled: Boolean,
    val skySightSatelliteEnabled: Boolean,
    val skySightSatelliteImageryEnabled: Boolean,
    val skySightSatelliteRadarEnabled: Boolean,
    val skySightSatelliteLightningEnabled: Boolean,
    val skySightSatelliteAnimateEnabled: Boolean,
    val skySightSatelliteHistoryFrames: Int,
    val weatherRainEnabled: Boolean,
    val weatherRainStatusCode: WeatherRadarStatusCode,
    val weatherRainStale: Boolean,
    val weatherRainFrameSelected: Boolean,
    val weatherRainTransitionDurationMs: Long
)
