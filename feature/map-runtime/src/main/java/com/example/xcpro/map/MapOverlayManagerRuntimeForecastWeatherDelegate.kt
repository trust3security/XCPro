package com.example.xcpro.map

import android.util.Log
import com.example.xcpro.forecast.FORECAST_OPACITY_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_ANIMATE_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_IMAGERY_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_LIGHTNING_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_OVERLAY_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_RADAR_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_WIND_DISPLAY_MODE_DEFAULT
import com.example.xcpro.forecast.FORECAST_WIND_OVERLAY_SCALE_DEFAULT
import com.example.xcpro.forecast.ForecastLegendSpec
import com.example.xcpro.forecast.ForecastTileFormat
import com.example.xcpro.forecast.ForecastTileSpec
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.forecast.clampForecastOpacity
import com.example.xcpro.forecast.clampForecastWindOverlayScale
import com.example.xcpro.forecast.clampSkySightSatelliteHistoryFrames
import com.example.xcpro.weather.rain.WEATHER_RAIN_OPACITY_DEFAULT
import com.example.xcpro.weather.rain.WEATHER_RAIN_STALE_DIMMED_OPACITY_MAX
import com.example.xcpro.weather.rain.WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import com.example.xcpro.weather.rain.clampWeatherRainOpacity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class MapOverlayManagerRuntimeForecastWeatherDelegate(
    private val runtimeState: ForecastWeatherOverlayRuntimeState,
    private val bringTrafficOverlaysToFront: () -> Unit,
    private val onSatelliteContrastIconsChanged: (Boolean) -> Unit,
    private val nowMonoMs: () -> Long
) {
    private var forecastOverlayEnabled: Boolean = false
    private var forecastWindOverlayEnabled: Boolean = false
    private var latestForecastPrimaryTileSpec: ForecastTileSpec? = null
    private var latestForecastPrimaryLegend: ForecastLegendSpec? = null
    private var latestForecastWindTileSpec: ForecastTileSpec? = null
    private var latestForecastWindLegend: ForecastLegendSpec? = null
    private var forecastOpacity: Float = FORECAST_OPACITY_DEFAULT
    private var forecastWindOverlayScale: Float = FORECAST_WIND_OVERLAY_SCALE_DEFAULT
    private var forecastWindDisplayMode: ForecastWindDisplayMode = FORECAST_WIND_DISPLAY_MODE_DEFAULT
    private var skySightSatelliteEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_OVERLAY_ENABLED_DEFAULT
    private var skySightSatelliteImageryEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_IMAGERY_ENABLED_DEFAULT
    private var skySightSatelliteRadarEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_RADAR_ENABLED_DEFAULT
    private var skySightSatelliteLightningEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_LIGHTNING_ENABLED_DEFAULT
    private var skySightSatelliteAnimateEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_ANIMATE_ENABLED_DEFAULT
    private var skySightSatelliteHistoryFrames: Int = FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_DEFAULT
    private var skySightSatelliteReferenceTimeUtcMs: Long? = null
    private var satelliteContrastIconsEnabled: Boolean = false
    private var lastSkySightSatelliteConfig: SkySightSatelliteRuntimeConfig? = null
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
    private val _forecastRuntimeWarningMessage = MutableStateFlow<String?>(null)
    val forecastRuntimeWarningMessage: StateFlow<String?> = _forecastRuntimeWarningMessage.asStateFlow()
    private val _skySightSatelliteRuntimeErrorMessage = MutableStateFlow<String?>(null)
    val skySightSatelliteRuntimeErrorMessage: StateFlow<String?> =
        _skySightSatelliteRuntimeErrorMessage.asStateFlow()

    fun setMapInteractionActive(active: Boolean) {
        if (mapInteractionActive == active) return
        mapInteractionActive = active
        if (!active) {
            flushDeferredWeatherRainConfig()
            runtimeState.mapLibreMap?.let(::reapplySkySightSatelliteOverlay)
        }
    }

    fun satelliteContrastIconsEnabled(): Boolean = satelliteContrastIconsEnabled

    fun onMapStyleChanged(map: MapLibreMap?) {
        if (map == null) return
        applyForecastWeatherStyleChange(
            runtimeState = runtimeState,
            map = map,
            reapplyForecastOverlay = ::reapplyForecastOverlay,
            reapplySkySightSatelliteOverlay = ::reapplySkySightSatelliteOverlay,
            reapplyWeatherRainOverlay = ::reapplyWeatherRainOverlay
        )
    }

    fun onInitialize(map: MapLibreMap?) {
        if (map == null) return
        initializeForecastWeatherOverlays(
            runtimeState = runtimeState,
            map = map,
            reapplyForecastOverlay = ::reapplyForecastOverlay,
            reapplySkySightSatelliteOverlay = ::reapplySkySightSatelliteOverlay,
            reapplyWeatherRainOverlay = ::reapplyWeatherRainOverlay
        )
    }

    fun onMapDetached() {
        deferredWeatherRainConfig = null
        _forecastRuntimeWarningMessage.value = null
        _skySightSatelliteRuntimeErrorMessage.value = null
    }

    fun setForecastOverlay(
        enabled: Boolean,
        primaryTileSpec: ForecastTileSpec?,
        primaryLegendSpec: ForecastLegendSpec?,
        windOverlayEnabled: Boolean,
        windTileSpec: ForecastTileSpec?,
        windLegendSpec: ForecastLegendSpec?,
        opacity: Float,
        windOverlayScale: Float,
        windDisplayMode: ForecastWindDisplayMode
    ) {
        val primaryOverlayEnabled = enabled
        forecastOverlayEnabled = primaryOverlayEnabled || windOverlayEnabled
        forecastWindOverlayEnabled = windOverlayEnabled
        latestForecastPrimaryTileSpec = primaryTileSpec
        latestForecastPrimaryLegend = primaryLegendSpec
        latestForecastWindTileSpec = windTileSpec
        latestForecastWindLegend = windLegendSpec
        forecastOpacity = clampForecastOpacity(opacity)
        forecastWindOverlayScale = clampForecastWindOverlayScale(windOverlayScale)
        forecastWindDisplayMode = windDisplayMode
        if (!forecastOverlayEnabled) {
            runtimeState.forecastOverlay?.clear()
            runtimeState.forecastWindOverlay?.clear()
            _forecastRuntimeWarningMessage.value = null
            return
        }
        val map = runtimeState.mapLibreMap ?: run {
            _forecastRuntimeWarningMessage.value = null
            return
        }
        if (runtimeState.forecastOverlay == null) {
            runtimeState.forecastOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "primary"
            )
        }
        if (runtimeState.forecastWindOverlay == null) {
            runtimeState.forecastWindOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "wind"
            )
        }
        var applyFailureMessage: String? = null
        if (primaryOverlayEnabled && primaryTileSpec != null) {
            applyFailureMessage = joinNonBlankRuntimeMessages(
                applyFailureMessage,
                renderForecastRasterOverlaySafely(
                    overlay = runtimeState.forecastOverlay,
                    tileSpec = primaryTileSpec,
                    opacity = forecastOpacity,
                    windOverlayScale = forecastWindOverlayScale,
                    windDisplayMode = forecastWindDisplayMode,
                    legendSpec = primaryLegendSpec,
                    fallbackErrorMessage = "Forecast overlay failed to apply",
                    onFailure = { throwable ->
                        Log.e(TAG, "Forecast overlay apply failed: ${throwable.message}", throwable)
                    }
                )
            )
        } else {
            runtimeState.forecastOverlay?.clear()
        }
        if (windOverlayEnabled && windTileSpec != null) {
            applyFailureMessage = joinNonBlankRuntimeMessages(
                applyFailureMessage,
                renderForecastRasterOverlaySafely(
                    overlay = runtimeState.forecastWindOverlay,
                    tileSpec = windTileSpec,
                    opacity = forecastOpacity,
                    windOverlayScale = forecastWindOverlayScale,
                    windDisplayMode = forecastWindDisplayMode,
                    legendSpec = windLegendSpec,
                    fallbackErrorMessage = "Forecast wind overlay failed to apply",
                    onFailure = { throwable ->
                        Log.e(TAG, "Forecast wind overlay apply failed: ${throwable.message}", throwable)
                    }
                )
            )
        } else {
            runtimeState.forecastWindOverlay?.clear()
        }
        refreshForecastRuntimeWarningMessage(applyFailureMessage)
        bringTrafficOverlaysToFront()
    }

    fun clearForecastOverlay() {
        forecastOverlayEnabled = false
        forecastWindOverlayEnabled = false
        latestForecastPrimaryTileSpec = null
        latestForecastPrimaryLegend = null
        latestForecastWindTileSpec = null
        latestForecastWindLegend = null
        runtimeState.forecastOverlay?.clear()
        runtimeState.forecastWindOverlay?.clear()
        _forecastRuntimeWarningMessage.value = null
    }

    fun setSkySightSatelliteOverlay(
        enabled: Boolean,
        showSatelliteImagery: Boolean,
        showRadar: Boolean,
        showLightning: Boolean,
        animate: Boolean,
        historyFrameCount: Int,
        referenceTimeUtcMs: Long?
    ) {
        val hasAnySatelliteLayer = showSatelliteImagery || showRadar || showLightning
        val nextContrastIconsEnabled = enabled && hasAnySatelliteLayer
        val resolvedHistoryFrames = clampSkySightSatelliteHistoryFrames(historyFrameCount)
        skySightSatelliteEnabled = enabled
        skySightSatelliteImageryEnabled = showSatelliteImagery
        skySightSatelliteRadarEnabled = showRadar
        skySightSatelliteLightningEnabled = showLightning
        skySightSatelliteAnimateEnabled = animate
        skySightSatelliteHistoryFrames = resolvedHistoryFrames
        skySightSatelliteReferenceTimeUtcMs = referenceTimeUtcMs
        val runtimeAnimate = if (mapInteractionActive) false else animate
        val nextConfig = SkySightSatelliteRuntimeConfig(
            enabled = enabled,
            showSatelliteImagery = showSatelliteImagery,
            showRadar = showRadar,
            showLightning = showLightning,
            animate = runtimeAnimate,
            historyFrameCount = resolvedHistoryFrames,
            referenceTimeUtcMs = referenceTimeUtcMs
        )
        if (!enabled || !hasAnySatelliteLayer) {
            runtimeState.skySightSatelliteOverlay?.clear()
            _skySightSatelliteRuntimeErrorMessage.value = null
            lastSkySightSatelliteConfig = nextConfig
            setSatelliteContrastIconsEnabled(nextContrastIconsEnabled)
            return
        }
        if (nextConfig == lastSkySightSatelliteConfig) return
        val map = runtimeState.mapLibreMap ?: run {
            _skySightSatelliteRuntimeErrorMessage.value = null
            return
        }
        if (applySkySightSatelliteOverlay(map, nextConfig)) {
            lastSkySightSatelliteConfig = nextConfig
            setSatelliteContrastIconsEnabled(nextContrastIconsEnabled)
        }
    }

    fun clearSkySightSatelliteOverlay() {
        skySightSatelliteEnabled = FORECAST_SKYSIGHT_SATELLITE_OVERLAY_ENABLED_DEFAULT
        skySightSatelliteImageryEnabled = FORECAST_SKYSIGHT_SATELLITE_IMAGERY_ENABLED_DEFAULT
        skySightSatelliteRadarEnabled = FORECAST_SKYSIGHT_SATELLITE_RADAR_ENABLED_DEFAULT
        skySightSatelliteLightningEnabled = FORECAST_SKYSIGHT_SATELLITE_LIGHTNING_ENABLED_DEFAULT
        skySightSatelliteAnimateEnabled = FORECAST_SKYSIGHT_SATELLITE_ANIMATE_ENABLED_DEFAULT
        skySightSatelliteHistoryFrames = FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_DEFAULT
        skySightSatelliteReferenceTimeUtcMs = null
        lastSkySightSatelliteConfig = null
        setSatelliteContrastIconsEnabled(false)
        runtimeState.skySightSatelliteOverlay?.clear()
        _skySightSatelliteRuntimeErrorMessage.value = null
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

    fun reapplySkySightSatelliteOverlay() {
        runtimeState.mapLibreMap?.let(::reapplySkySightSatelliteOverlay)
    }

    fun reapplyForecastOverlay() {
        runtimeState.mapLibreMap?.let(::reapplyForecastOverlay)
    }

    fun findForecastWindArrowSpeedAt(tap: LatLng): Double? {
        if (!forecastOverlayEnabled || !forecastWindOverlayEnabled) return null
        val tileSpec = latestForecastWindTileSpec ?: return null
        if (tileSpec.format != ForecastTileFormat.VECTOR_WIND_POINTS || forecastWindDisplayMode != ForecastWindDisplayMode.ARROW) {
            return null
        }
        return runtimeState.forecastWindOverlay?.findWindArrowSpeedAt(tap)
    }

    fun statusSnapshot(): MapOverlayForecastWeatherStatus = MapOverlayForecastWeatherStatus(
        forecastOverlayEnabled = forecastOverlayEnabled,
        forecastWindOverlayEnabled = forecastWindOverlayEnabled,
        satelliteContrastIconsEnabled = satelliteContrastIconsEnabled,
        skySightSatelliteEnabled = skySightSatelliteEnabled,
        skySightSatelliteImageryEnabled = skySightSatelliteImageryEnabled,
        skySightSatelliteRadarEnabled = skySightSatelliteRadarEnabled,
        skySightSatelliteLightningEnabled = skySightSatelliteLightningEnabled,
        skySightSatelliteAnimateEnabled = skySightSatelliteAnimateEnabled,
        skySightSatelliteHistoryFrames = skySightSatelliteHistoryFrames,
        weatherRainEnabled = weatherRainEnabled,
        weatherRainStatusCode = weatherRainStatusCode,
        weatherRainStale = weatherRainStale,
        weatherRainFrameSelected = weatherRainFrameSelection != null,
        weatherRainTransitionDurationMs = weatherRainTransitionDurationMs
    )

    private fun reapplyForecastOverlay(map: MapLibreMap) {
        if (!forecastOverlayEnabled) {
            runtimeState.forecastOverlay?.clear()
            runtimeState.forecastWindOverlay?.clear()
            _forecastRuntimeWarningMessage.value = null
            return
        }
        if (runtimeState.forecastOverlay == null) {
            runtimeState.forecastOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "primary"
            )
        }
        if (runtimeState.forecastWindOverlay == null) {
            runtimeState.forecastWindOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "wind"
            )
        }
        var applyFailureMessage: String? = null
        val primaryTileSpec = latestForecastPrimaryTileSpec
        if (primaryTileSpec != null) {
            applyFailureMessage = joinNonBlankRuntimeMessages(
                applyFailureMessage,
                renderForecastRasterOverlaySafely(
                    overlay = runtimeState.forecastOverlay,
                    tileSpec = primaryTileSpec,
                    opacity = forecastOpacity,
                    windOverlayScale = forecastWindOverlayScale,
                    windDisplayMode = forecastWindDisplayMode,
                    legendSpec = latestForecastPrimaryLegend,
                    fallbackErrorMessage = "Forecast overlay failed to apply",
                    onFailure = { throwable ->
                        Log.e(TAG, "Forecast overlay reapply failed: ${throwable.message}", throwable)
                    }
                )
            )
        } else {
            runtimeState.forecastOverlay?.clear()
        }
        val windTileSpec = latestForecastWindTileSpec
        if (forecastWindOverlayEnabled && windTileSpec != null) {
            applyFailureMessage = joinNonBlankRuntimeMessages(
                applyFailureMessage,
                renderForecastRasterOverlaySafely(
                    overlay = runtimeState.forecastWindOverlay,
                    tileSpec = windTileSpec,
                    opacity = forecastOpacity,
                    windOverlayScale = forecastWindOverlayScale,
                    windDisplayMode = forecastWindDisplayMode,
                    legendSpec = latestForecastWindLegend,
                    fallbackErrorMessage = "Forecast wind overlay failed to apply",
                    onFailure = { throwable ->
                        Log.e(TAG, "Forecast wind overlay reapply failed: ${throwable.message}", throwable)
                    }
                )
            )
        } else {
            runtimeState.forecastWindOverlay?.clear()
        }
        refreshForecastRuntimeWarningMessage(applyFailureMessage)
        bringTrafficOverlaysToFront()
    }

    private fun refreshForecastRuntimeWarningMessage(applyFailureMessage: String? = null) {
        _forecastRuntimeWarningMessage.value = joinNonBlankRuntimeMessages(
            runtimeState.forecastOverlay?.runtimeWarningMessage(),
            runtimeState.forecastWindOverlay?.runtimeWarningMessage(),
            applyFailureMessage
        )
    }

    private fun reapplySkySightSatelliteOverlay(map: MapLibreMap) {
        val config = SkySightSatelliteRuntimeConfig(
            enabled = skySightSatelliteEnabled,
            showSatelliteImagery = skySightSatelliteImageryEnabled,
            showRadar = skySightSatelliteRadarEnabled,
            showLightning = skySightSatelliteLightningEnabled,
            animate = if (mapInteractionActive) false else skySightSatelliteAnimateEnabled,
            historyFrameCount = skySightSatelliteHistoryFrames,
            referenceTimeUtcMs = skySightSatelliteReferenceTimeUtcMs
        )
        if (applySkySightSatelliteOverlay(map, config)) {
            lastSkySightSatelliteConfig = config
            setSatelliteContrastIconsEnabled(
                config.enabled && (config.showSatelliteImagery || config.showRadar || config.showLightning)
            )
        }
    }

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

    private fun applySkySightSatelliteOverlay(
        map: MapLibreMap,
        config: SkySightSatelliteRuntimeConfig
    ): Boolean {
        return applySkySightSatelliteOverlayRuntime(
            runtimeState = runtimeState,
            map = map,
            config = config,
            onRuntimeErrorChanged = { _skySightSatelliteRuntimeErrorMessage.value = it },
            bringTrafficOverlaysToFront = bringTrafficOverlaysToFront
        )
    }

    private fun applyWeatherRainOverlay(
        map: MapLibreMap,
        config: WeatherRainRuntimeConfig
    ): Boolean {
        return applyWeatherRainOverlayRuntime(
            runtimeState = runtimeState,
            map = map,
            config = config,
            bringTrafficOverlaysToFront = bringTrafficOverlaysToFront
        )
    }

    private fun setSatelliteContrastIconsEnabled(enabled: Boolean) {
        if (satelliteContrastIconsEnabled == enabled) return
        satelliteContrastIconsEnabled = enabled
        onSatelliteContrastIconsChanged(enabled)
    }

    private fun flushDeferredWeatherRainConfig() {
        val deferred = deferredWeatherRainConfig ?: return
        deferredWeatherRainConfig = null
        val map = runtimeState.mapLibreMap ?: return
        if (applyWeatherRainOverlay(map, deferred)) {
            lastWeatherRainConfig = deferred
            lastWeatherRainApplyMonoMs = nowMonoMs()
        }
    }

    private companion object {
        private const val TAG = "MapOverlayManager"
    }
}
