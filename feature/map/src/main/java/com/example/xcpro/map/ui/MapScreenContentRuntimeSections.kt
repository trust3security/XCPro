package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.xcpro.forecast.ForecastOverlayUiState
import com.example.xcpro.forecast.ForecastOverlayViewModel
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.weglide.ui.WeGlideUploadPromptDialogHost
import kotlin.math.roundToInt

@Composable
internal fun MapBottomTabsSection(
    selectedBottomTab: MapBottomTab,
    isBottomTabsSheetVisible: Boolean,
    isTaskPanelVisible: Boolean,
    hasTrafficDetailsOpen: Boolean,
    setSelectedBottomTabName: (String) -> Unit,
    setBottomTabsSheetVisible: (Boolean) -> Unit,
    onDismissOgnTargetDetails: () -> Unit,
    onDismissOgnThermalDetails: () -> Unit,
    onDismissAdsbTargetDetails: () -> Unit,
    weatherEnabled: Boolean,
    ognOverlayEnabled: Boolean,
    showOgnSciaEnabled: Boolean,
    onToggleOgnScia: () -> Unit,
    adsbOverlayEnabled: Boolean,
    showOgnThermalsEnabled: Boolean,
    showDistanceCircles: Boolean,
    currentQnhLabel: String,
    onToggleAdsbTraffic: () -> Unit,
    onToggleOgnThermals: () -> Unit,
    overlayManager: MapOverlayManager,
    openQnhDialog: () -> Unit,
    ognTrailAircraftRows: List<OgnTrailAircraftRowUi>,
    onOgnTrailAircraftToggled: (String, Boolean) -> Unit,
    forecastOverlayState: ForecastOverlayUiState,
    forecastViewModel: ForecastOverlayViewModel,
    skySightWarningMessage: String?,
    skySightErrorMessage: String?,
    skySightSatViewEnabled: Boolean,
    currentMapStyleName: String,
    lastNonSatelliteMapStyleName: String?,
    setLastNonSatelliteMapStyleName: (String?) -> Unit,
    onTransientMapStyleSelected: (String) -> Unit
) {
    MapBottomTabsLayer(
        selectedTab = selectedBottomTab,
        isSheetVisible = isBottomTabsSheetVisible,
        isTaskPanelVisible = isTaskPanelVisible,
        onTabSelected = { tab ->
            setSelectedBottomTabName(tab.name)
            if (hasTrafficDetailsOpen) {
                onDismissOgnTargetDetails()
                onDismissOgnThermalDetails()
                onDismissAdsbTargetDetails()
            }
            if (!isTaskPanelVisible) {
                setBottomTabsSheetVisible(true)
            }
        },
        onDismissSheet = { setBottomTabsSheetVisible(false) },
        weatherEnabled = weatherEnabled,
        ognEnabled = ognOverlayEnabled,
        showSciaEnabled = showOgnSciaEnabled,
        onShowSciaEnabledChanged = { enabled ->
            if (enabled != showOgnSciaEnabled) onToggleOgnScia()
        },
        adsbTrafficEnabled = adsbOverlayEnabled,
        showOgnThermalsEnabled = showOgnThermalsEnabled,
        showDistanceCircles = showDistanceCircles,
        currentQnhLabel = currentQnhLabel,
        onAdsbTrafficEnabledChanged = { enabled ->
            if (enabled != adsbOverlayEnabled) onToggleAdsbTraffic()
        },
        onShowOgnThermalsEnabledChanged = { enabled ->
            if (enabled != showOgnThermalsEnabled) onToggleOgnThermals()
        },
        onShowDistanceCirclesChanged = { enabled ->
            if (enabled != showDistanceCircles) {
                overlayManager.toggleDistanceCircles()
            }
        },
        onOpenQnhDialogFromTab = openQnhDialog,
        ognTrailAircraftRows = ognTrailAircraftRows,
        onOgnTrailAircraftToggled = onOgnTrailAircraftToggled,
        skySightUiState = forecastOverlayState,
        onSkySightEnabledChanged = forecastViewModel::setEnabled,
        onSkySightPrimaryParameterToggled = forecastViewModel::selectSkySightPrimaryParameter,
        onSkySightWindOverlayEnabledChanged = forecastViewModel::setWindOverlayEnabled,
        onSkySightWindParameterSelected = forecastViewModel::selectWindParameter,
        onSkySightAutoTimeEnabledChanged = forecastViewModel::setAutoTimeEnabled,
        onSkySightFollowTimeOffsetChanged = forecastViewModel::setFollowTimeOffsetMinutes,
        onSkySightJumpToNow = forecastViewModel::jumpToNow,
        onSkySightTimeSelected = forecastViewModel::selectTime,
        onSkySightSatelliteOverlayEnabledChanged = forecastViewModel::setSkySightSatelliteOverlayEnabled,
        onSkySightSatelliteImageryEnabledChanged = forecastViewModel::setSkySightSatelliteImageryEnabled,
        onSkySightSatelliteRadarEnabledChanged = forecastViewModel::setSkySightSatelliteRadarEnabled,
        onSkySightSatelliteLightningEnabledChanged = forecastViewModel::setSkySightSatelliteLightningEnabled,
        onSkySightSatelliteAnimateEnabledChanged = forecastViewModel::setSkySightSatelliteAnimateEnabled,
        onSkySightSatelliteHistoryFramesChanged = forecastViewModel::setSkySightSatelliteHistoryFrames,
        skySightWarningMessage = skySightWarningMessage,
        skySightErrorMessage = skySightErrorMessage,
        skySightSatViewEnabled = skySightSatViewEnabled,
        onSkySightSatViewEnabledChanged = { enabled ->
            if (enabled) {
                if (!skySightSatViewEnabled) {
                    setLastNonSatelliteMapStyleName(currentMapStyleName)
                    onTransientMapStyleSelected(SATELLITE_MAP_STYLE_NAME)
                }
            } else {
                val restoreStyle = lastNonSatelliteMapStyleName
                    ?.takeIf { style ->
                        !style.equals(SATELLITE_MAP_STYLE_NAME, ignoreCase = true)
                    }
                    ?: DEFAULT_NON_SATELLITE_MAP_STYLE_NAME
                onTransientMapStyleSelected(restoreStyle)
            }
        }
    )
}

@Composable
internal fun BoxScope.MapAuxiliaryPanelsAndSheetsSection(
    inputs: MapAuxiliaryPanelsInputs
) {
    inputs.tappedWindArrowCallout?.let { callout ->
        val map = inputs.mapState.mapLibreMap
        val screenPoint = map?.projection?.toScreenLocation(callout.tapLatLng)
        if (screenPoint != null) {
            val edgePaddingPx = with(inputs.density) { WIND_TAP_LABEL_EDGE_PADDING_DP.dp.toPx() }
            val anchorGapPx = with(inputs.density) { WIND_TAP_LABEL_ANCHOR_GAP_DP.dp.toPx() }
            val estimatedWidthPx = with(inputs.density) { WIND_TAP_LABEL_ESTIMATED_WIDTH_DP.dp.toPx() }
            val estimatedHeightPx = with(inputs.density) { WIND_TAP_LABEL_ESTIMATED_HEIGHT_DP.dp.toPx() }
            val labelWidthPx = if (inputs.windTapLabelSize.width > 0) {
                inputs.windTapLabelSize.width.toFloat()
            } else {
                estimatedWidthPx
            }
            val labelHeightPx = if (inputs.windTapLabelSize.height > 0) {
                inputs.windTapLabelSize.height.toFloat()
            } else {
                estimatedHeightPx
            }
            val maxX = (inputs.overlayViewportSize.width.toFloat() - labelWidthPx - edgePaddingPx)
                .coerceAtLeast(edgePaddingPx)
            val maxY = (inputs.overlayViewportSize.height.toFloat() - labelHeightPx - edgePaddingPx)
                .coerceAtLeast(edgePaddingPx)
            val targetX = (screenPoint.x - (labelWidthPx / 2f))
                .coerceIn(edgePaddingPx, maxX)
            val targetY = (screenPoint.y - labelHeightPx - anchorGapPx)
                .coerceIn(edgePaddingPx, maxY)

            WindArrowSpeedTapLabel(
                speedKt = callout.speedKt,
                unitLabel = inputs.forecastWindUnitLabel,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = targetX.roundToInt(),
                            y = targetY.roundToInt()
                        )
                    }
                    .onSizeChanged { size ->
                        inputs.onWindTapLabelSizeChanged(size)
                    }
            )
        }
    }

    inputs.forecastPointCallout?.let { callout ->
        ForecastPointCalloutCard(
            callout = callout,
            regionCode = inputs.forecastSelectedRegionCode,
            onDismiss = inputs.onDismissForecastPointCallout,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp, start = 16.dp, end = 16.dp)
        )
    }

    inputs.forecastQueryStatus?.let { status ->
        ForecastQueryStatusChip(
            message = status,
            onDismiss = inputs.onDismissForecastQueryStatus,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp, start = 16.dp, end = 16.dp)
        )
    }

    QnhDialogHost(
        visible = inputs.qnhDialog.visible,
        qnhInput = inputs.qnhDialog.input,
        qnhError = inputs.qnhDialog.error,
        unitsPreferences = inputs.qnhDialog.unitsPreferences,
        liveData = inputs.qnhDialog.liveFlightData,
        calibrationState = inputs.qnhDialog.calibrationState,
        onQnhInputChange = inputs.qnhDialog.onInputChange,
        onConfirm = inputs.qnhDialog.onConfirm,
        onInvalidInput = inputs.qnhDialog.onInvalidInput,
        onAutoCalibrate = inputs.qnhDialog.onAutoCalibrate,
        onDismiss = inputs.qnhDialog.onDismiss
    )

    WeGlideUploadPromptDialogHost(
        prompt = inputs.weGlidePrompt.prompt,
        onConfirm = inputs.weGlidePrompt.onConfirm,
        onDismiss = inputs.weGlidePrompt.onDismiss
    )
}
