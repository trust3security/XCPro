package com.example.xcpro.map.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.example.xcpro.core.flight.RealTimeFlightData
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.forecast.ForecastPointCallout
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.qnh.QnhCalibrationState
import com.example.xcpro.weglide.ui.WeGlideUploadPromptUiState

internal data class MapAuxiliaryPanelsInputs(
    val mapState: MapScreenState,
    val density: Density,
    val tappedWindArrowCallout: WindArrowTapCallout?,
    val forecastWindUnitLabel: String,
    val windTapLabelSize: IntSize,
    val onWindTapLabelSizeChanged: (IntSize) -> Unit,
    val overlayViewportSize: IntSize,
    val forecastPointCallout: ForecastPointCallout?,
    val forecastSelectedRegionCode: String,
    val onDismissForecastPointCallout: () -> Unit,
    val forecastQueryStatus: String?,
    val onDismissForecastQueryStatus: () -> Unit,
    val qnhDialog: MapQnhDialogInputs,
    val weGlidePrompt: MapWeGlidePromptInputs
)

internal data class MapQnhDialogInputs(
    val visible: Boolean,
    val input: String,
    val error: String?,
    val unitsPreferences: UnitsPreferences,
    val liveFlightData: RealTimeFlightData?,
    val calibrationState: QnhCalibrationState,
    val onInputChange: (String) -> Unit,
    val onConfirm: (Double) -> Unit,
    val onInvalidInput: (String) -> Unit,
    val onAutoCalibrate: () -> Unit,
    val onDismiss: () -> Unit
)

internal data class MapWeGlidePromptInputs(
    val prompt: WeGlideUploadPromptUiState?,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit
)
