package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.forecast.ForecastOverlayUiState
import com.example.xcpro.forecast.forecastRegionLabel
import com.example.xcpro.forecast.forecastRegionLikelyContainsCoordinate
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.ogn.OgnTrafficSnapshot
import kotlinx.coroutines.delay
import org.maplibre.android.maps.MapLibreMap

internal data class TrafficDebugPanelVisibility(
    val showOgnDebugPanel: Boolean,
    val showAdsbDebugPanel: Boolean,
    val showAdsbIssueFlash: Boolean,
    val showAdsbPersistentStatus: Boolean
)

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
    overlayManager: MapOverlayManager
) {
    LaunchedEffect(
        mapLibreMap,
        forecastOverlayState.enabled,
        forecastOverlayState.primaryTileSpec,
        forecastOverlayState.primaryLegend,
        forecastOverlayState.windOverlayEnabled,
        forecastOverlayState.windTileSpec,
        forecastOverlayState.windLegend,
        forecastOverlayState.opacity,
        forecastOverlayState.windOverlayScale,
        forecastOverlayState.windDisplayMode
    ) {
        val hasPrimaryOverlay = forecastOverlayState.enabled &&
            forecastOverlayState.primaryTileSpec != null
        val hasWindOverlay = forecastOverlayState.windOverlayEnabled &&
            forecastOverlayState.windTileSpec != null
        if (!hasPrimaryOverlay && !hasWindOverlay) {
            overlayManager.clearForecastOverlay()
            return@LaunchedEffect
        }
        overlayManager.setForecastOverlay(
            enabled = forecastOverlayState.enabled,
            primaryTileSpec = forecastOverlayState.primaryTileSpec,
            primaryLegendSpec = forecastOverlayState.primaryLegend,
            windOverlayEnabled = forecastOverlayState.windOverlayEnabled,
            windTileSpec = forecastOverlayState.windTileSpec,
            windLegendSpec = forecastOverlayState.windLegend,
            opacity = forecastOverlayState.opacity,
            windOverlayScale = forecastOverlayState.windOverlayScale,
            windDisplayMode = forecastOverlayState.windDisplayMode
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

@Composable
internal fun rememberTrafficDebugPanelVisibility(
    adsbOverlayEnabled: Boolean,
    adsbSnapshot: AdsbTrafficSnapshot,
    ognOverlayEnabled: Boolean,
    ognSnapshot: OgnTrafficSnapshot
): TrafficDebugPanelVisibility {
    val showOgnDebugPanel = rememberTimedVisibility(
        enabled = BuildConfig.DEBUG &&
            ognOverlayEnabled &&
            shouldSurfaceOgnDebugPanel(ognSnapshot),
        readyForAutoDismiss = isOgnReadyForAutoDismiss(ognSnapshot),
        autoDismissDelayMs = TRAFFIC_DEBUG_PANEL_AUTO_DISMISS_MS
    )
    val showAdsbDebugPanel = rememberTimedVisibility(
        enabled = BuildConfig.DEBUG &&
            adsbOverlayEnabled &&
            shouldSurfaceAdsbDebugPanel(adsbSnapshot),
        readyForAutoDismiss = isAdsbReadyForAutoDismiss(adsbSnapshot) ||
            shouldFlashAdsbIssue(adsbSnapshot),
        autoDismissDelayMs = ADSB_ISSUE_FLASH_AUTO_DISMISS_MS
    )
    val showAdsbIssueFlash = rememberTimedVisibility(
        enabled = adsbOverlayEnabled &&
            shouldFlashAdsbIssue(adsbSnapshot),
        readyForAutoDismiss = true,
        autoDismissDelayMs = ADSB_ISSUE_FLASH_AUTO_DISMISS_MS
    )
    val showAdsbPersistentStatus = rememberPersistentIssueVisibility(
        enabled = adsbOverlayEnabled,
        issueActive = shouldSurfacePersistentAdsbStatus(adsbSnapshot),
        healthy = isAdsbReadyForAutoDismiss(adsbSnapshot),
        recoveryDwellMs = ADSB_PERSISTENT_STATUS_RECOVERY_DISMISS_MS
    )
    return TrafficDebugPanelVisibility(
        showOgnDebugPanel = showOgnDebugPanel,
        showAdsbDebugPanel = showAdsbDebugPanel,
        showAdsbIssueFlash = showAdsbIssueFlash,
        showAdsbPersistentStatus = showAdsbPersistentStatus
    )
}
