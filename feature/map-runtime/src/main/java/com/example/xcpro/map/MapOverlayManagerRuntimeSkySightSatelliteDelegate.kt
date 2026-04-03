package com.example.xcpro.map

import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_ANIMATE_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_IMAGERY_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_LIGHTNING_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_OVERLAY_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_RADAR_ENABLED_DEFAULT
import com.example.xcpro.forecast.clampSkySightSatelliteHistoryFrames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.maps.MapLibreMap

internal class MapOverlayManagerRuntimeSkySightSatelliteDelegate(
    private val runtimeState: ForecastWeatherOverlayRuntimeState,
    private val bringTrafficOverlaysToFront: () -> Unit,
    private val onSatelliteContrastIconsChanged: (Boolean) -> Unit
) {
    private var skySightSatelliteEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_OVERLAY_ENABLED_DEFAULT
    private var skySightSatelliteImageryEnabled: Boolean =
        FORECAST_SKYSIGHT_SATELLITE_IMAGERY_ENABLED_DEFAULT
    private var skySightSatelliteRadarEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_RADAR_ENABLED_DEFAULT
    private var skySightSatelliteLightningEnabled: Boolean =
        FORECAST_SKYSIGHT_SATELLITE_LIGHTNING_ENABLED_DEFAULT
    private var skySightSatelliteAnimateEnabled: Boolean =
        FORECAST_SKYSIGHT_SATELLITE_ANIMATE_ENABLED_DEFAULT
    private var skySightSatelliteHistoryFrames: Int =
        FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_DEFAULT
    private var skySightSatelliteReferenceTimeUtcMs: Long? = null
    private var satelliteContrastIconsEnabled: Boolean = false
    private var lastSkySightSatelliteConfig: SkySightSatelliteRuntimeConfig? = null
    private var pendingInteractionReleaseReapply: Boolean = false
    private var mapInteractionActive: Boolean = false
    private val _skySightSatelliteRuntimeErrorMessage = MutableStateFlow<String?>(null)
    val skySightSatelliteRuntimeErrorMessage: StateFlow<String?> =
        _skySightSatelliteRuntimeErrorMessage.asStateFlow()

    fun setMapInteractionActive(active: Boolean) {
        if (mapInteractionActive == active) return
        mapInteractionActive = active
        pendingInteractionReleaseReapply = !active && shouldReapplyAfterInteractionRelease()
    }

    fun satelliteContrastIconsEnabled(): Boolean = satelliteContrastIconsEnabled

    fun onMapStyleChanged(map: MapLibreMap?) {
        if (map == null) return
        runtimeState.skySightSatelliteOverlay?.cleanup()
        runtimeState.skySightSatelliteOverlay = SkySightSatelliteOverlay(map)
        reapplySkySightSatelliteOverlay(map)
    }

    fun onInitialize(map: MapLibreMap?) {
        if (map == null) return
        runtimeState.skySightSatelliteOverlay = SkySightSatelliteOverlay(map)
        reapplySkySightSatelliteOverlay(map)
    }

    fun onMapDetached() {
        mapInteractionActive = false
        pendingInteractionReleaseReapply = false
        _skySightSatelliteRuntimeErrorMessage.value = null
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

    fun reapplySkySightSatelliteOverlay() {
        runtimeState.mapLibreMap?.let(::reapplySkySightSatelliteOverlay)
    }

    fun flushInteractionReleaseReapplyIfNeeded(reconcileFrontOrder: Boolean = true): Boolean {
        if (!pendingInteractionReleaseReapply) return false
        pendingInteractionReleaseReapply = false
        val map = runtimeState.mapLibreMap ?: return false
        return reapplySkySightSatelliteOverlay(map, reconcileFrontOrder = reconcileFrontOrder)
    }

    fun statusSnapshot(): SkySightSatelliteRuntimeStatus = SkySightSatelliteRuntimeStatus(
        contrastIconsEnabled = satelliteContrastIconsEnabled,
        enabled = skySightSatelliteEnabled,
        showSatelliteImagery = skySightSatelliteImageryEnabled,
        showRadar = skySightSatelliteRadarEnabled,
        showLightning = skySightSatelliteLightningEnabled,
        animate = skySightSatelliteAnimateEnabled,
        historyFrameCount = skySightSatelliteHistoryFrames
    )

    private fun reapplySkySightSatelliteOverlay(
        map: MapLibreMap,
        reconcileFrontOrder: Boolean = true
    ): Boolean {
        val config = SkySightSatelliteRuntimeConfig(
            enabled = skySightSatelliteEnabled,
            showSatelliteImagery = skySightSatelliteImageryEnabled,
            showRadar = skySightSatelliteRadarEnabled,
            showLightning = skySightSatelliteLightningEnabled,
            animate = if (mapInteractionActive) false else skySightSatelliteAnimateEnabled,
            historyFrameCount = skySightSatelliteHistoryFrames,
            referenceTimeUtcMs = skySightSatelliteReferenceTimeUtcMs
        )
        if (applySkySightSatelliteOverlay(map, config, reconcileFrontOrder = reconcileFrontOrder)) {
            lastSkySightSatelliteConfig = config
            setSatelliteContrastIconsEnabled(
                config.enabled && (config.showSatelliteImagery || config.showRadar || config.showLightning)
            )
            return true
        }
        return false
    }

    private fun applySkySightSatelliteOverlay(
        map: MapLibreMap,
        config: SkySightSatelliteRuntimeConfig,
        reconcileFrontOrder: Boolean = true
    ): Boolean {
        return applySkySightSatelliteOverlayRuntime(
            runtimeState = runtimeState,
            map = map,
            config = config,
            onRuntimeErrorChanged = { _skySightSatelliteRuntimeErrorMessage.value = it },
            bringTrafficOverlaysToFront = bringTrafficOverlaysToFront,
            reconcileFrontOrder = reconcileFrontOrder
        )
    }

    private fun shouldReapplyAfterInteractionRelease(): Boolean {
        if (!skySightSatelliteEnabled) return false
        if (!skySightSatelliteAnimateEnabled) return false
        return skySightSatelliteImageryEnabled || skySightSatelliteRadarEnabled || skySightSatelliteLightningEnabled
    }

    private fun setSatelliteContrastIconsEnabled(enabled: Boolean) {
        if (satelliteContrastIconsEnabled == enabled) return
        satelliteContrastIconsEnabled = enabled
        onSatelliteContrastIconsChanged(enabled)
    }
}
