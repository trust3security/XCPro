package com.trust3.xcpro.map

import android.util.Log
import com.trust3.xcpro.weather.rain.WEATHER_RAIN_STALE_DIMMED_OPACITY_MAX
import org.maplibre.android.maps.MapLibreMap

internal fun applySkySightSatelliteOverlayRuntime(
    runtimeState: ForecastWeatherOverlayRuntimeState,
    map: MapLibreMap,
    config: SkySightSatelliteRuntimeConfig,
    onRuntimeErrorChanged: (String?) -> Unit,
    bringTrafficOverlaysToFront: () -> Unit,
    reconcileFrontOrder: Boolean = true
): Boolean {
    val hasAnySatelliteLayer = config.showSatelliteImagery || config.showRadar || config.showLightning
    if (!config.enabled || !hasAnySatelliteLayer) {
        runtimeState.skySightSatelliteOverlay?.clear()
        onRuntimeErrorChanged(null)
        return true
    }
    if (runtimeState.skySightSatelliteOverlay == null) {
        runtimeState.skySightSatelliteOverlay = SkySightSatelliteOverlay(map)
    }
    return runCatching {
        runtimeState.skySightSatelliteOverlay?.render(
            SkySightSatelliteRenderConfig(
                enabled = config.enabled,
                showSatelliteImagery = config.showSatelliteImagery,
                showRadar = config.showRadar,
                showLightning = config.showLightning,
                animate = config.animate,
                historyFrameCount = config.historyFrameCount,
                referenceTimeUtcMs = config.referenceTimeUtcMs
            )
        )
        onRuntimeErrorChanged(null)
        if (reconcileFrontOrder) {
            bringTrafficOverlaysToFront()
        }
        true
    }.getOrElse { throwable ->
        onRuntimeErrorChanged(
            throwable.message?.trim()?.takeIf { it.isNotEmpty() }
                ?: "SkySight satellite overlay failed to apply"
        )
        Log.e("MapOverlayManager", "SkySight satellite overlay apply failed: ${throwable.message}", throwable)
        false
    }
}

internal fun applyWeatherRainOverlayRuntime(
    runtimeState: ForecastWeatherOverlayRuntimeState,
    map: MapLibreMap,
    config: WeatherRainRuntimeConfig,
    bringTrafficOverlaysToFront: () -> Unit,
    reconcileFrontOrder: Boolean = true
): Boolean {
    val frameSelection = config.frameSelection
    if (!config.enabled || frameSelection == null) {
        runtimeState.weatherRainOverlay?.clear()
        return true
    }
    if (runtimeState.weatherRainOverlay == null) {
        runtimeState.weatherRainOverlay = WeatherRainOverlay(map)
    }
    val effectiveOpacity = if (config.stale) {
        minOf(config.opacity, WEATHER_RAIN_STALE_DIMMED_OPACITY_MAX)
    } else {
        config.opacity
    }
    return runCatching {
        runtimeState.weatherRainOverlay?.render(
            frameSelection = frameSelection,
            opacity = effectiveOpacity,
            transitionDurationMs = config.transitionDurationMs
        )
        if (reconcileFrontOrder) {
            bringTrafficOverlaysToFront()
        }
        true
    }.getOrElse { throwable ->
        Log.e("MapOverlayManager", "Weather rain overlay apply failed: ${throwable.message}", throwable)
        false
    }
}
