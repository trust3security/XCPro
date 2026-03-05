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
internal class MapOverlayManagerRuntimeForecastWeatherDelegate(
    private val mapState: MapScreenState,
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
            mapState.mapLibreMap?.let(::reapplySkySightSatelliteOverlay)
        }
    }

    fun satelliteContrastIconsEnabled(): Boolean = satelliteContrastIconsEnabled
    fun onMapStyleChanged(map: MapLibreMap?) {
        if (map == null) return
        applyForecastWeatherStyleChange(
            mapState = mapState,
            map = map,
            reapplyForecastOverlay = ::reapplyForecastOverlay,
            reapplySkySightSatelliteOverlay = ::reapplySkySightSatelliteOverlay,
            reapplyWeatherRainOverlay = ::reapplyWeatherRainOverlay
        )
    }
    fun onInitialize(map: MapLibreMap?) {
        if (map == null) return
        initializeForecastWeatherOverlays(
            mapState = mapState,
            map = map,
            reapplyForecastOverlay = ::reapplyForecastOverlay,
            reapplySkySightSatelliteOverlay = ::reapplySkySightSatelliteOverlay,
            reapplyWeatherRainOverlay = ::reapplyWeatherRainOverlay
        )
    }
    fun onMapDetached() {
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
            mapState.forecastOverlay?.clear()
            mapState.forecastWindOverlay?.clear()
            _forecastRuntimeWarningMessage.value = null
            return
        }
        val map = mapState.mapLibreMap ?: run {
            _forecastRuntimeWarningMessage.value = null
            return
        }
        if (mapState.forecastOverlay == null) {
            mapState.forecastOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "primary"
            )
        }
        if (mapState.forecastWindOverlay == null) {
            mapState.forecastWindOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "wind"
            )
        }
        var applyFailureMessage: String? = null
        if (primaryOverlayEnabled && primaryTileSpec != null) {
            applyFailureMessage = joinNonBlankRuntimeMessages(
                applyFailureMessage,
                renderForecastRasterOverlaySafely(
                    overlay = mapState.forecastOverlay,
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
            mapState.forecastOverlay?.clear()
        }
        if (windOverlayEnabled && windTileSpec != null) {
            applyFailureMessage = joinNonBlankRuntimeMessages(
                applyFailureMessage,
                renderForecastRasterOverlaySafely(
                    overlay = mapState.forecastWindOverlay,
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
            mapState.forecastWindOverlay?.clear()
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
        mapState.forecastOverlay?.clear()
        mapState.forecastWindOverlay?.clear()
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
            mapState.skySightSatelliteOverlay?.clear()
            _skySightSatelliteRuntimeErrorMessage.value = null
            lastSkySightSatelliteConfig = nextConfig
            setSatelliteContrastIconsEnabled(nextContrastIconsEnabled)
            return
        }
        if (nextConfig == lastSkySightSatelliteConfig) return
        val map = mapState.mapLibreMap ?: run {
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
        mapState.skySightSatelliteOverlay?.clear()
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
        val map = mapState.mapLibreMap ?: return
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
            if (!mapInteractionActive) {
                deferredWeatherRainConfig = null
            }
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
        mapState.weatherRainOverlay?.clear()
    }
    fun reapplyWeatherRainOverlay() { mapState.mapLibreMap?.let(::reapplyWeatherRainOverlay) }
    fun reapplySkySightSatelliteOverlay() { mapState.mapLibreMap?.let(::reapplySkySightSatelliteOverlay) }
    fun reapplyForecastOverlay() { mapState.mapLibreMap?.let(::reapplyForecastOverlay) }
    fun findForecastWindArrowSpeedAt(tap: LatLng): Double? {
        if (!forecastOverlayEnabled) return null
        if (!forecastWindOverlayEnabled) return null
        val tileSpec = latestForecastWindTileSpec ?: return null
        if (tileSpec.format != ForecastTileFormat.VECTOR_WIND_POINTS) return null
        if (forecastWindDisplayMode != ForecastWindDisplayMode.ARROW) return null
        return mapState.forecastWindOverlay?.findWindArrowSpeedAt(tap)
    }
    fun statusSnapshot(): MapOverlayForecastWeatherStatus = MapOverlayForecastWeatherStatus(
        forecastOverlayEnabled,
        forecastWindOverlayEnabled,
        satelliteContrastIconsEnabled,
        skySightSatelliteEnabled,
        skySightSatelliteImageryEnabled,
        skySightSatelliteRadarEnabled,
        skySightSatelliteLightningEnabled,
        skySightSatelliteAnimateEnabled,
        skySightSatelliteHistoryFrames,
        weatherRainEnabled,
        weatherRainStatusCode,
        weatherRainStale,
        weatherRainFrameSelection != null,
        weatherRainTransitionDurationMs
    )
    private fun reapplyForecastOverlay(map: MapLibreMap) {
        if (!forecastOverlayEnabled) {
            mapState.forecastOverlay?.clear()
            mapState.forecastWindOverlay?.clear()
            _forecastRuntimeWarningMessage.value = null
            return
        }
        if (mapState.forecastOverlay == null) {
            mapState.forecastOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "primary"
            )
        }
        if (mapState.forecastWindOverlay == null) {
            mapState.forecastWindOverlay = ForecastRasterOverlay(
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
                    overlay = mapState.forecastOverlay,
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
            mapState.forecastOverlay?.clear()
        }
        val windTileSpec = latestForecastWindTileSpec
        if (forecastWindOverlayEnabled && windTileSpec != null) {
            applyFailureMessage = joinNonBlankRuntimeMessages(
                applyFailureMessage,
                renderForecastRasterOverlaySafely(
                    overlay = mapState.forecastWindOverlay,
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
            mapState.forecastWindOverlay?.clear()
        }
        refreshForecastRuntimeWarningMessage(applyFailureMessage)
        bringTrafficOverlaysToFront()
    }
    private fun refreshForecastRuntimeWarningMessage(applyFailureMessage: String? = null) {
        _forecastRuntimeWarningMessage.value = joinNonBlankRuntimeMessages(
            mapState.forecastOverlay?.runtimeWarningMessage(),
            mapState.forecastWindOverlay?.runtimeWarningMessage(),
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
            if (!mapInteractionActive) {
                deferredWeatherRainConfig = null
            }
        } else {
            lastWeatherRainConfig = null
        }
    }
    private fun applySkySightSatelliteOverlay(
        map: MapLibreMap,
        config: SkySightSatelliteRuntimeConfig
    ): Boolean {
        val hasAnySatelliteLayer = config.showSatelliteImagery || config.showRadar || config.showLightning
        if (!config.enabled || !hasAnySatelliteLayer) {
            mapState.skySightSatelliteOverlay?.clear()
            _skySightSatelliteRuntimeErrorMessage.value = null
            return true
        }
        if (mapState.skySightSatelliteOverlay == null) {
            mapState.skySightSatelliteOverlay = SkySightSatelliteOverlay(map)
        }
        return runCatching {
            mapState.skySightSatelliteOverlay?.render(
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
            _skySightSatelliteRuntimeErrorMessage.value = null
            bringTrafficOverlaysToFront()
            true
        }.getOrElse { throwable ->
            _skySightSatelliteRuntimeErrorMessage.value =
                throwable.message?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "SkySight satellite overlay failed to apply"
            Log.e(TAG, "SkySight satellite overlay apply failed: ${throwable.message}", throwable)
            false
        }
    }
    private fun applyWeatherRainOverlay(
        map: MapLibreMap,
        config: WeatherRainRuntimeConfig
    ): Boolean {
        val frameSelection = config.frameSelection
        if (!config.enabled || frameSelection == null) {
            mapState.weatherRainOverlay?.clear()
            return true
        }
        if (mapState.weatherRainOverlay == null) {
            mapState.weatherRainOverlay = WeatherRainOverlay(map)
        }
        val effectiveOpacity = if (config.stale) {
            minOf(config.opacity, WEATHER_RAIN_STALE_DIMMED_OPACITY_MAX)
        } else {
            config.opacity
        }
        return runCatching {
            mapState.weatherRainOverlay?.render(
                frameSelection = frameSelection,
                opacity = effectiveOpacity,
                transitionDurationMs = config.transitionDurationMs
            )
            bringTrafficOverlaysToFront()
            true
        }.getOrElse { throwable ->
            Log.e(TAG, "Weather rain overlay apply failed: ${throwable.message}", throwable)
            false
        }
    }
    private fun setSatelliteContrastIconsEnabled(enabled: Boolean) {
        if (satelliteContrastIconsEnabled == enabled) return
        satelliteContrastIconsEnabled = enabled
        onSatelliteContrastIconsChanged(enabled)
    }

    private fun flushDeferredWeatherRainConfig() {
        val deferred = deferredWeatherRainConfig ?: return
        val map = mapState.mapLibreMap ?: return
        deferredWeatherRainConfig = null
        if (applyWeatherRainOverlay(map, deferred)) {
            lastWeatherRainConfig = deferred
            lastWeatherRainApplyMonoMs = nowMonoMs()
        }
    }

    private companion object {
        private const val TAG = "MapOverlayManager"
    }
}
