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
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.convertQnhInputToHpa
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.forecast.ForecastOverlayUiState
import com.example.xcpro.forecast.ForecastOverlayViewModel
import com.example.xcpro.forecast.ForecastPointCallout
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.WeGlideUploadPromptUiState
import com.example.xcpro.qnh.QnhCalibrationState
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
    mapState: MapScreenState,
    density: Density,
    tappedWindArrowCallout: WindArrowTapCallout?,
    forecastOverlayState: ForecastOverlayUiState,
    windTapLabelSize: IntSize,
    setWindTapLabelSize: (IntSize) -> Unit,
    overlayViewportSize: IntSize,
    forecastPointCallout: ForecastPointCallout?,
    forecastQueryStatus: String?,
    forecastViewModel: ForecastOverlayViewModel,
    showQnhDialog: Boolean,
    qnhInput: String,
    qnhError: String?,
    unitsPreferences: UnitsPreferences,
    liveFlightData: RealTimeFlightData?,
    qnhCalibrationState: QnhCalibrationState,
    weGlideUploadPrompt: WeGlideUploadPromptUiState?,
    setQnhInput: (String) -> Unit,
    setQnhError: (String?) -> Unit,
    setShowQnhDialog: (Boolean) -> Unit,
    onSetManualQnh: (Double) -> Unit,
    onAutoCalibrateQnh: () -> Unit,
    onConfirmWeGlideUploadPrompt: () -> Unit,
    onDismissWeGlideUploadPrompt: () -> Unit
) {
    tappedWindArrowCallout?.let { callout ->
        val map = mapState.mapLibreMap
        val screenPoint = map?.projection?.toScreenLocation(callout.tapLatLng)
        if (screenPoint != null) {
            val edgePaddingPx = with(density) { WIND_TAP_LABEL_EDGE_PADDING_DP.dp.toPx() }
            val anchorGapPx = with(density) { WIND_TAP_LABEL_ANCHOR_GAP_DP.dp.toPx() }
            val estimatedWidthPx = with(density) { WIND_TAP_LABEL_ESTIMATED_WIDTH_DP.dp.toPx() }
            val estimatedHeightPx = with(density) { WIND_TAP_LABEL_ESTIMATED_HEIGHT_DP.dp.toPx() }
            val labelWidthPx = if (windTapLabelSize.width > 0) {
                windTapLabelSize.width.toFloat()
            } else {
                estimatedWidthPx
            }
            val labelHeightPx = if (windTapLabelSize.height > 0) {
                windTapLabelSize.height.toFloat()
            } else {
                estimatedHeightPx
            }
            val maxX = (overlayViewportSize.width.toFloat() - labelWidthPx - edgePaddingPx)
                .coerceAtLeast(edgePaddingPx)
            val maxY = (overlayViewportSize.height.toFloat() - labelHeightPx - edgePaddingPx)
                .coerceAtLeast(edgePaddingPx)
            val targetX = (screenPoint.x - (labelWidthPx / 2f))
                .coerceIn(edgePaddingPx, maxX)
            val targetY = (screenPoint.y - labelHeightPx - anchorGapPx)
                .coerceIn(edgePaddingPx, maxY)

            WindArrowSpeedTapLabel(
                speedKt = callout.speedKt,
                unitLabel = forecastOverlayState.windLegend?.unitLabel
                    ?.takeIf { label -> label.isNotBlank() }
                    ?: DEFAULT_WIND_SPEED_UNIT_LABEL,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = targetX.roundToInt(),
                            y = targetY.roundToInt()
                        )
                    }
                    .onSizeChanged { size ->
                        setWindTapLabelSize(size)
                    }
            )
        }
    }

    forecastPointCallout?.let { callout ->
        ForecastPointCalloutCard(
            callout = callout,
            regionCode = forecastOverlayState.selectedRegionCode,
            onDismiss = forecastViewModel::clearPointCallout,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp, start = 16.dp, end = 16.dp)
        )
    }

    forecastQueryStatus?.let { status ->
        ForecastQueryStatusChip(
            message = status,
            onDismiss = forecastViewModel::clearQueryStatus,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp, start = 16.dp, end = 16.dp)
        )
    }

    QnhDialogHost(
        visible = showQnhDialog,
        qnhInput = qnhInput,
        qnhError = qnhError,
        unitsPreferences = unitsPreferences,
        liveData = liveFlightData,
        calibrationState = qnhCalibrationState,
        onQnhInputChange = {
            setQnhInput(it)
            setQnhError(null)
        },
        onConfirm = { parsed ->
            val qnhHpa = convertQnhInputToHpa(parsed, unitsPreferences)
            onSetManualQnh(qnhHpa)
            setShowQnhDialog(false)
            setQnhError(null)
        },
        onInvalidInput = { error ->
            setQnhError(error)
        },
        onAutoCalibrate = {
            onAutoCalibrateQnh()
            setShowQnhDialog(false)
            setQnhError(null)
        },
        onDismiss = {
            setShowQnhDialog(false)
            setQnhError(null)
        }
    )

    WeGlideUploadPromptDialogHost(
        prompt = weGlideUploadPrompt,
        onConfirm = onConfirmWeGlideUploadPrompt,
        onDismiss = onDismissWeGlideUploadPrompt
    )
}
