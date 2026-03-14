package com.example.xcpro.map

import com.example.xcpro.forecast.ForecastLegendSpec
import com.example.xcpro.forecast.ForecastTileSpec
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class MapOverlayManagerRuntimeForecastWeatherDelegate(
    runtimeState: ForecastWeatherOverlayRuntimeState,
    bringTrafficOverlaysToFront: () -> Unit,
    onSatelliteContrastIconsChanged: (Boolean) -> Unit,
    nowMonoMs: () -> Long
) {
    private val forecastDelegate = MapOverlayManagerRuntimeForecastRasterDelegate(
        runtimeState = runtimeState,
        bringTrafficOverlaysToFront = bringTrafficOverlaysToFront
    )
    private val skySightSatelliteDelegate = MapOverlayManagerRuntimeSkySightSatelliteDelegate(
        runtimeState = runtimeState,
        bringTrafficOverlaysToFront = bringTrafficOverlaysToFront,
        onSatelliteContrastIconsChanged = onSatelliteContrastIconsChanged
    )
    private val weatherRainDelegate = MapOverlayManagerRuntimeWeatherRainDelegate(
        runtimeState = runtimeState,
        bringTrafficOverlaysToFront = bringTrafficOverlaysToFront,
        nowMonoMs = nowMonoMs
    )

    val forecastRuntimeWarningMessage: StateFlow<String?> =
        forecastDelegate.forecastRuntimeWarningMessage
    val skySightSatelliteRuntimeErrorMessage: StateFlow<String?> =
        skySightSatelliteDelegate.skySightSatelliteRuntimeErrorMessage

    fun setMapInteractionActive(active: Boolean) {
        skySightSatelliteDelegate.setMapInteractionActive(active)
        weatherRainDelegate.setMapInteractionActive(active)
    }

    fun satelliteContrastIconsEnabled(): Boolean =
        skySightSatelliteDelegate.satelliteContrastIconsEnabled()

    fun onMapStyleChanged(map: MapLibreMap?) {
        forecastDelegate.onMapStyleChanged(map)
        skySightSatelliteDelegate.onMapStyleChanged(map)
        weatherRainDelegate.onMapStyleChanged(map)
    }

    fun onInitialize(map: MapLibreMap?) {
        forecastDelegate.onInitialize(map)
        skySightSatelliteDelegate.onInitialize(map)
        weatherRainDelegate.onInitialize(map)
    }

    fun onMapDetached() {
        weatherRainDelegate.onMapDetached()
        skySightSatelliteDelegate.onMapDetached()
        forecastDelegate.onMapDetached()
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
    ) = forecastDelegate.setForecastOverlay(
        enabled = enabled,
        primaryTileSpec = primaryTileSpec,
        primaryLegendSpec = primaryLegendSpec,
        windOverlayEnabled = windOverlayEnabled,
        windTileSpec = windTileSpec,
        windLegendSpec = windLegendSpec,
        opacity = opacity,
        windOverlayScale = windOverlayScale,
        windDisplayMode = windDisplayMode
    )

    fun clearForecastOverlay() = forecastDelegate.clearForecastOverlay()

    fun setSkySightSatelliteOverlay(
        enabled: Boolean,
        showSatelliteImagery: Boolean,
        showRadar: Boolean,
        showLightning: Boolean,
        animate: Boolean,
        historyFrameCount: Int,
        referenceTimeUtcMs: Long?
    ) = skySightSatelliteDelegate.setSkySightSatelliteOverlay(
        enabled = enabled,
        showSatelliteImagery = showSatelliteImagery,
        showRadar = showRadar,
        showLightning = showLightning,
        animate = animate,
        historyFrameCount = historyFrameCount,
        referenceTimeUtcMs = referenceTimeUtcMs
    )

    fun clearSkySightSatelliteOverlay() = skySightSatelliteDelegate.clearSkySightSatelliteOverlay()

    fun setWeatherRainOverlay(
        enabled: Boolean,
        frameSelection: WeatherRainFrameSelection?,
        opacity: Float,
        transitionDurationMs: Long,
        statusCode: WeatherRadarStatusCode,
        stale: Boolean
    ) = weatherRainDelegate.setWeatherRainOverlay(
        enabled = enabled,
        frameSelection = frameSelection,
        opacity = opacity,
        transitionDurationMs = transitionDurationMs,
        statusCode = statusCode,
        stale = stale
    )

    fun clearWeatherRainOverlay() = weatherRainDelegate.clearWeatherRainOverlay()

    fun reapplyWeatherRainOverlay() = weatherRainDelegate.reapplyWeatherRainOverlay()

    fun reapplySkySightSatelliteOverlay() = skySightSatelliteDelegate.reapplySkySightSatelliteOverlay()

    fun reapplyForecastOverlay() = forecastDelegate.reapplyForecastOverlay()

    fun findForecastWindArrowSpeedAt(tap: LatLng): Double? =
        forecastDelegate.findForecastWindArrowSpeedAt(tap)

    fun statusSnapshot(): MapOverlayForecastWeatherStatus {
        val forecastStatus = forecastDelegate.statusSnapshot()
        val satelliteStatus = skySightSatelliteDelegate.statusSnapshot()
        val weatherRainStatus = weatherRainDelegate.statusSnapshot()
        return MapOverlayForecastWeatherStatus(
            forecastOverlayEnabled = forecastStatus.overlayEnabled,
            forecastWindOverlayEnabled = forecastStatus.windOverlayEnabled,
            satelliteContrastIconsEnabled = satelliteStatus.contrastIconsEnabled,
            skySightSatelliteEnabled = satelliteStatus.enabled,
            skySightSatelliteImageryEnabled = satelliteStatus.showSatelliteImagery,
            skySightSatelliteRadarEnabled = satelliteStatus.showRadar,
            skySightSatelliteLightningEnabled = satelliteStatus.showLightning,
            skySightSatelliteAnimateEnabled = satelliteStatus.animate,
            skySightSatelliteHistoryFrames = satelliteStatus.historyFrameCount,
            weatherRainEnabled = weatherRainStatus.enabled,
            weatherRainStatusCode = weatherRainStatus.statusCode,
            weatherRainStale = weatherRainStatus.stale,
            weatherRainFrameSelected = weatherRainStatus.frameSelected,
            weatherRainTransitionDurationMs = weatherRainStatus.transitionDurationMs
        )
    }
}
