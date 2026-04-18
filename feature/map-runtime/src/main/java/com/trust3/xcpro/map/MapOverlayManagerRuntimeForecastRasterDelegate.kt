package com.trust3.xcpro.map

import android.util.Log
import com.trust3.xcpro.forecast.FORECAST_OPACITY_DEFAULT
import com.trust3.xcpro.forecast.FORECAST_WIND_DISPLAY_MODE_DEFAULT
import com.trust3.xcpro.forecast.FORECAST_WIND_OVERLAY_SCALE_DEFAULT
import com.trust3.xcpro.forecast.ForecastLegendSpec
import com.trust3.xcpro.forecast.ForecastTileFormat
import com.trust3.xcpro.forecast.ForecastTileSpec
import com.trust3.xcpro.forecast.ForecastWindDisplayMode
import com.trust3.xcpro.forecast.clampForecastOpacity
import com.trust3.xcpro.forecast.clampForecastWindOverlayScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

internal class MapOverlayManagerRuntimeForecastRasterDelegate(
    private val runtimeState: ForecastWeatherOverlayRuntimeState,
    private val bringTrafficOverlaysToFront: () -> Unit
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
    private val _forecastRuntimeWarningMessage = MutableStateFlow<String?>(null)
    val forecastRuntimeWarningMessage: StateFlow<String?> = _forecastRuntimeWarningMessage.asStateFlow()

    fun onMapStyleChanged(map: MapLibreMap?) {
        if (map == null) return
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
    }

    fun onInitialize(map: MapLibreMap?) {
        if (map == null) return
        runtimeState.forecastOverlay = ForecastRasterOverlay(
            map = map,
            idNamespace = "primary"
        )
        runtimeState.forecastWindOverlay = ForecastRasterOverlay(
            map = map,
            idNamespace = "wind"
        )
        reapplyForecastOverlay(map)
    }

    fun onMapDetached() {
        _forecastRuntimeWarningMessage.value = null
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

    fun reapplyForecastOverlay() {
        runtimeState.mapLibreMap?.let(::reapplyForecastOverlay)
    }

    fun findForecastWindArrowSpeedAt(tap: LatLng): Double? {
        if (!forecastOverlayEnabled || !forecastWindOverlayEnabled) return null
        val tileSpec = latestForecastWindTileSpec ?: return null
        if (tileSpec.format != ForecastTileFormat.VECTOR_WIND_POINTS ||
            forecastWindDisplayMode != ForecastWindDisplayMode.ARROW
        ) {
            return null
        }
        return runtimeState.forecastWindOverlay?.findWindArrowSpeedAt(tap)
    }

    fun statusSnapshot(): ForecastRasterRuntimeStatus = ForecastRasterRuntimeStatus(
        overlayEnabled = forecastOverlayEnabled,
        windOverlayEnabled = forecastWindOverlayEnabled
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

    private companion object {
        private const val TAG = "MapOverlayManager"
    }
}
