package com.example.xcpro.map.ui

import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import com.example.xcpro.variometer.layout.VariometerUiState

internal data class MapWidgetOffsetStates(
    val hamburgerOffset: MutableState<Offset>,
    val flightModeOffset: MutableState<Offset>,
    val settingsOffset: MutableState<Offset>,
    val ballastOffset: MutableState<Offset>,
    val hamburgerSizePx: MutableState<Float>,
    val settingsSizePx: MutableState<Float>
)

internal data class VariometerLayoutState(
    val uiState: VariometerUiState,
    val minSizePx: Float,
    val maxSizePx: Float
)

internal data class MapScreenWidgetLayoutBinding(
    val screenWidthPx: Float,
    val screenHeightPx: Float,
    val hamburgerOffsetState: MutableState<Offset>,
    val flightModeOffsetState: MutableState<Offset>,
    val settingsOffsetState: MutableState<Offset>,
    val ballastOffsetState: MutableState<Offset>,
    val hamburgerSizePxState: MutableState<Float>,
    val settingsSizePxState: MutableState<Float>,
    val onHamburgerOffsetChange: (Offset) -> Unit,
    val onFlightModeOffsetChange: (Offset) -> Unit,
    val onSettingsOffsetChange: (Offset) -> Unit,
    val onBallastOffsetChange: (Offset) -> Unit,
    val onHamburgerSizeChange: (Float) -> Unit,
    val onSettingsSizeChange: (Float) -> Unit
)
