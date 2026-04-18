package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.trust3.xcpro.forecast.ForecastOverlayUiState
import com.trust3.xcpro.forecast.ForecastLegendSpec
import com.trust3.xcpro.forecast.ForecastTileSpec
import com.trust3.xcpro.forecast.ForecastWindDisplayMode
import com.trust3.xcpro.forecast.forecastRegionLabel
import com.trust3.xcpro.forecast.forecastRegionLikelyContainsCoordinate
import com.trust3.xcpro.map.MapOverlayManager
import com.trust3.xcpro.map.model.MapLocationUiModel
import kotlinx.coroutines.delay
import org.maplibre.android.maps.MapLibreMap

internal data class ForecastOverlayRuntimeDispatch(
    val shouldClear: Boolean,
    val setConfig: ForecastOverlaySetConfig?,
    val arbitrationWarningMessage: String?
)

internal data class ForecastOverlaySetConfig(
    val enabled: Boolean,
    val primaryTileSpec: ForecastTileSpec?,
    val primaryLegendSpec: ForecastLegendSpec?,
    val windOverlayEnabled: Boolean,
    val windTileSpec: ForecastTileSpec?,
    val windLegendSpec: ForecastLegendSpec?,
    val opacity: Float,
    val windOverlayScale: Float,
    val windDisplayMode: ForecastWindDisplayMode
)

internal fun computeSkySightRainSuppressionWarning(
    forecastOverlayState: ForecastOverlayUiState,
    rainViewerEnabled: Boolean
): String? {
    val rainSelected = forecastOverlayState.selectedPrimaryParameterId.value.equals(
        SKY_SIGHT_RAIN_PARAMETER_ID,
        ignoreCase = true
    )
    val suppressed = rainViewerEnabled && forecastOverlayState.enabled && rainSelected
    return if (suppressed) {
        SKY_SIGHT_RAIN_ARBITRATION_WARNING
    } else {
        null
    }
}

internal fun computeForecastOverlayRuntimeDispatch(
    forecastOverlayState: ForecastOverlayUiState,
    rainViewerEnabled: Boolean
): ForecastOverlayRuntimeDispatch {
    val arbitrationWarningMessage = computeSkySightRainSuppressionWarning(
        forecastOverlayState = forecastOverlayState,
        rainViewerEnabled = rainViewerEnabled
    )
    val primarySuppressedByRainViewer = arbitrationWarningMessage != null
    val effectivePrimaryEnabled = forecastOverlayState.enabled && !primarySuppressedByRainViewer
    val hasPrimaryOverlay = effectivePrimaryEnabled &&
        forecastOverlayState.primaryTileSpec != null
    val hasWindOverlay = forecastOverlayState.windOverlayEnabled &&
        forecastOverlayState.windTileSpec != null
    val overlaysRequested = effectivePrimaryEnabled || forecastOverlayState.windOverlayEnabled
    if (!hasPrimaryOverlay && !hasWindOverlay) {
        if (overlaysRequested && forecastOverlayState.isLoading) {
            return ForecastOverlayRuntimeDispatch(
                shouldClear = false,
                setConfig = null,
                arbitrationWarningMessage = arbitrationWarningMessage
            )
        }
        return ForecastOverlayRuntimeDispatch(
            shouldClear = true,
            setConfig = null,
            arbitrationWarningMessage = arbitrationWarningMessage
        )
    }
    return ForecastOverlayRuntimeDispatch(
        shouldClear = false,
        setConfig = ForecastOverlaySetConfig(
            enabled = effectivePrimaryEnabled,
            primaryTileSpec = forecastOverlayState.primaryTileSpec,
            primaryLegendSpec = forecastOverlayState.primaryLegend,
            windOverlayEnabled = forecastOverlayState.windOverlayEnabled,
            windTileSpec = forecastOverlayState.windTileSpec,
            windLegendSpec = forecastOverlayState.windLegend,
            opacity = forecastOverlayState.opacity,
            windOverlayScale = forecastOverlayState.windOverlayScale,
            windDisplayMode = forecastOverlayState.windDisplayMode
        ),
        arbitrationWarningMessage = arbitrationWarningMessage
    )
}

internal fun computeSkySightRegionCoverageWarning(
    mapLibreMap: MapLibreMap?,
    fallbackLocation: MapLocationUiModel?,
    regionCode: String
): String? {
    val mapCenterLatitude = mapLibreMap?.cameraPosition?.target?.latitude
        ?: fallbackLocation?.latitude
    val mapCenterLongitude = mapLibreMap?.cameraPosition?.target?.longitude
        ?: fallbackLocation?.longitude
    if (
        mapCenterLatitude == null ||
        mapCenterLongitude == null ||
        forecastRegionLikelyContainsCoordinate(
            regionCode = regionCode,
            latitude = mapCenterLatitude,
            longitude = mapCenterLongitude
        )
    ) {
        return null
    }
    return "Map center appears outside ${forecastRegionLabel(regionCode)} coverage; SkySight overlays may be unavailable."
}

@Composable
internal fun ForecastOverlayRuntimeEffects(
    mapLibreMap: MapLibreMap?,
    forecastOverlayState: ForecastOverlayUiState,
    rainViewerEnabled: Boolean,
    overlayManager: MapOverlayManager
) {
    LaunchedEffect(
        mapLibreMap,
        forecastOverlayState.enabled,
        forecastOverlayState.isLoading,
        forecastOverlayState.selectedPrimaryParameterId,
        forecastOverlayState.primaryTileSpec,
        forecastOverlayState.primaryLegend,
        forecastOverlayState.windOverlayEnabled,
        forecastOverlayState.windTileSpec,
        forecastOverlayState.windLegend,
        forecastOverlayState.opacity,
        forecastOverlayState.windOverlayScale,
        forecastOverlayState.windDisplayMode,
        rainViewerEnabled
    ) {
        val dispatch = computeForecastOverlayRuntimeDispatch(
            forecastOverlayState = forecastOverlayState,
            rainViewerEnabled = rainViewerEnabled
        )
        if (dispatch.shouldClear) {
            overlayManager.clearForecastOverlay()
            return@LaunchedEffect
        }
        val setConfig = dispatch.setConfig ?: return@LaunchedEffect
        overlayManager.setForecastOverlay(
            enabled = setConfig.enabled,
            primaryTileSpec = setConfig.primaryTileSpec,
            primaryLegendSpec = setConfig.primaryLegendSpec,
            windOverlayEnabled = setConfig.windOverlayEnabled,
            windTileSpec = setConfig.windTileSpec,
            windLegendSpec = setConfig.windLegendSpec,
            opacity = setConfig.opacity,
            windOverlayScale = setConfig.windOverlayScale,
            windDisplayMode = setConfig.windDisplayMode
        )
    }

    LaunchedEffect(
        mapLibreMap,
        forecastOverlayState.skySightSatelliteOverlayEnabled,
        forecastOverlayState.skySightSatelliteImageryEnabled,
        forecastOverlayState.skySightSatelliteRadarEnabled,
        forecastOverlayState.skySightSatelliteLightningEnabled,
        forecastOverlayState.skySightSatelliteAnimateEnabled,
        forecastOverlayState.skySightSatelliteHistoryFrames,
        forecastOverlayState.selectedTimeUtcMs
    ) {
        overlayManager.setSkySightSatelliteOverlay(
            enabled = forecastOverlayState.skySightSatelliteOverlayEnabled,
            showSatelliteImagery = forecastOverlayState.skySightSatelliteImageryEnabled,
            showRadar = forecastOverlayState.skySightSatelliteRadarEnabled,
            showLightning = forecastOverlayState.skySightSatelliteLightningEnabled,
            animate = forecastOverlayState.skySightSatelliteAnimateEnabled,
            historyFrameCount = forecastOverlayState.skySightSatelliteHistoryFrames,
            referenceTimeUtcMs = forecastOverlayState.selectedTimeUtcMs
        )
    }
}

private const val SKY_SIGHT_RAIN_PARAMETER_ID = "accrain"
internal const val SKY_SIGHT_RAIN_ARBITRATION_WARNING =
    "RainViewer rain is active; SkySight rain overlay is temporarily suppressed."

@Composable
internal fun WindArrowTapRuntimeEffects(
    isForecastWindArrowOverlayActive: Boolean,
    tappedWindArrowCallout: WindArrowTapCallout?,
    onClearTapCallout: () -> Unit,
    onResetWindTapLabelSize: () -> Unit
) {
    LaunchedEffect(isForecastWindArrowOverlayActive) {
        if (!isForecastWindArrowOverlayActive) {
            onClearTapCallout()
            onResetWindTapLabelSize()
        }
    }

    LaunchedEffect(tappedWindArrowCallout) {
        if (tappedWindArrowCallout != null) {
            delay(WIND_ARROW_SPEED_TAP_DISPLAY_MS)
            onClearTapCallout()
        }
    }
}
