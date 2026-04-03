package com.example.xcpro.map

import com.example.xcpro.weather.rain.WEATHER_RAIN_OPACITY_DEFAULT
import com.example.xcpro.weather.rain.WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import com.example.xcpro.weather.rain.clampWeatherRainOpacity
import org.maplibre.android.maps.MapLibreMap

internal class MapOverlayManagerRuntimeWeatherRainDelegate(
    private val runtimeState: ForecastWeatherOverlayRuntimeState,
    private val bringTrafficOverlaysToFront: () -> Unit,
    private val nowMonoMs: () -> Long
) {
    private var weatherRainEnabled: Boolean = false
    private var weatherRainOpacity: Float = WEATHER_RAIN_OPACITY_DEFAULT
    private var weatherRainTransitionDurationMs: Long = WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS
    private var weatherRainFrameSelection: WeatherRainFrameSelection? = null
    private var weatherRainStatusCode: WeatherRadarStatusCode = WeatherRadarStatusCode.NO_METADATA
    private var weatherRainStale: Boolean = true
    private var lastWeatherRainConfig: WeatherRainRuntimeConfig? = null
    private var deferredWeatherRainConfig: WeatherRainRuntimeConfig? = null
    private var lastWeatherRainApplyMonoMs: Long = 0L
    private var mapInteractionActive: Boolean = false

    fun setMapInteractionActive(active: Boolean) {
        if (mapInteractionActive == active) return
        mapInteractionActive = active
    }

    fun onMapStyleChanged(map: MapLibreMap?) {
        if (map == null) return
        runtimeState.weatherRainOverlay?.cleanup()
        runtimeState.weatherRainOverlay = WeatherRainOverlay(map)
        reapplyWeatherRainOverlay(map)
    }

    fun onInitialize(map: MapLibreMap?) {
        if (map == null) return
        runtimeState.weatherRainOverlay = WeatherRainOverlay(map)
        reapplyWeatherRainOverlay(map)
    }

    fun onMapDetached() {
        mapInteractionActive = false
        deferredWeatherRainConfig = null
        lastWeatherRainConfig = null
        lastWeatherRainApplyMonoMs = 0L
    }

    fun setWeatherRainOverlay(
        enabled: Boolean,
        frameSelection: WeatherRainFrameSelection?,
        opacity: Float,
        transitionDurationMs: Long,
        statusCode: WeatherRadarStatusCode,
        stale: Boolean
    ) {
        val resolvedOpacity = clampWeatherRainOpacity(opacity)
        val resolvedTransitionDurationMs = transitionDurationMs.coerceAtLeast(0L)
        weatherRainEnabled = enabled
        weatherRainFrameSelection = frameSelection
        weatherRainOpacity = resolvedOpacity
        weatherRainTransitionDurationMs = resolvedTransitionDurationMs
        weatherRainStatusCode = statusCode
        weatherRainStale = stale
        val nextConfig = WeatherRainRuntimeConfig(
            enabled = enabled,
            frameSelection = frameSelection,
            opacity = resolvedOpacity,
            transitionDurationMs = resolvedTransitionDurationMs,
            stale = stale
        )
        if (nextConfig == lastWeatherRainConfig) return
        val map = runtimeState.mapLibreMap ?: return
        if (shouldSkipWeatherRainApplyDuringInteraction(
                interactionActive = mapInteractionActive,
                enabled = nextConfig.enabled,
                hasFrameSelection = nextConfig.frameSelection != null,
                lastAppliedMonoMs = lastWeatherRainApplyMonoMs,
                nowMonoMs = nowMonoMs()
            )
        ) {
            deferredWeatherRainConfig = nextConfig
            return
        }
        val runtimeConfig = nextConfig.copy(
            transitionDurationMs = effectiveWeatherRainTransitionDurationMs(
                interactionActive = mapInteractionActive,
                requestedDurationMs = nextConfig.transitionDurationMs
            )
        )
        if (applyWeatherRainOverlay(map, runtimeConfig)) {
            lastWeatherRainConfig = runtimeConfig
            lastWeatherRainApplyMonoMs = nowMonoMs()
            deferredWeatherRainConfig = null
        } else {
            lastWeatherRainConfig = null
        }
    }

    fun clearWeatherRainOverlay() {
        weatherRainEnabled = false
        weatherRainFrameSelection = null
        weatherRainOpacity = WEATHER_RAIN_OPACITY_DEFAULT
        weatherRainTransitionDurationMs = WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS
        weatherRainStatusCode = WeatherRadarStatusCode.NO_METADATA
        weatherRainStale = true
        lastWeatherRainConfig = null
        deferredWeatherRainConfig = null
        lastWeatherRainApplyMonoMs = 0L
        runtimeState.weatherRainOverlay?.clear()
    }

    fun reapplyWeatherRainOverlay() {
        runtimeState.mapLibreMap?.let(::reapplyWeatherRainOverlay)
    }

    fun statusSnapshot(): WeatherRainRuntimeStatus = WeatherRainRuntimeStatus(
        enabled = weatherRainEnabled,
        statusCode = weatherRainStatusCode,
        stale = weatherRainStale,
        frameSelected = weatherRainFrameSelection != null,
        transitionDurationMs = weatherRainTransitionDurationMs
    )

    private fun reapplyWeatherRainOverlay(map: MapLibreMap) {
        val requestedConfig = WeatherRainRuntimeConfig(
            enabled = weatherRainEnabled,
            frameSelection = weatherRainFrameSelection,
            opacity = weatherRainOpacity,
            transitionDurationMs = weatherRainTransitionDurationMs,
            stale = weatherRainStale
        )
        if (shouldSkipWeatherRainApplyDuringInteraction(
                interactionActive = mapInteractionActive,
                enabled = requestedConfig.enabled,
                hasFrameSelection = requestedConfig.frameSelection != null,
                lastAppliedMonoMs = lastWeatherRainApplyMonoMs,
                nowMonoMs = nowMonoMs()
            )
        ) {
            deferredWeatherRainConfig = requestedConfig
            return
        }
        val runtimeConfig = requestedConfig.copy(
            transitionDurationMs = effectiveWeatherRainTransitionDurationMs(
                interactionActive = mapInteractionActive,
                requestedDurationMs = requestedConfig.transitionDurationMs
            )
        )
        if (applyWeatherRainOverlay(map, runtimeConfig)) {
            lastWeatherRainConfig = runtimeConfig
            lastWeatherRainApplyMonoMs = nowMonoMs()
            deferredWeatherRainConfig = null
        } else {
            lastWeatherRainConfig = null
        }
    }

    private fun applyWeatherRainOverlay(
        map: MapLibreMap,
        config: WeatherRainRuntimeConfig,
        reconcileFrontOrder: Boolean = true
    ): Boolean {
        return applyWeatherRainOverlayRuntime(
            runtimeState = runtimeState,
            map = map,
            config = config,
            bringTrafficOverlaysToFront = bringTrafficOverlaysToFront,
            reconcileFrontOrder = reconcileFrontOrder
        )
    }

    fun flushDeferredInteractionReleaseWork(reconcileFrontOrder: Boolean = true): Boolean {
        val deferred = deferredWeatherRainConfig ?: return false
        deferredWeatherRainConfig = null
        val map = runtimeState.mapLibreMap ?: return false
        val runtimeConfig = deferred.copy(
            transitionDurationMs = effectiveWeatherRainTransitionDurationMs(
                interactionActive = mapInteractionActive,
                requestedDurationMs = deferred.transitionDurationMs
            )
        )
        if (applyWeatherRainOverlay(map, runtimeConfig, reconcileFrontOrder = reconcileFrontOrder)) {
            lastWeatherRainConfig = runtimeConfig
            lastWeatherRainApplyMonoMs = nowMonoMs()
            return true
        }
        return false
    }
}
