package com.trust3.xcpro.map.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.ui1.VarioDialConfig
import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.map.ballast.BallastCommand
import com.trust3.xcpro.map.ballast.BallastUiState
import com.trust3.xcpro.variometer.layout.VariometerUiState

/**
 * Thin facade over individual widget implementations.
 * Public API remains the same; implementations live in separate files for readability.
 */
object MapUIWidgets {

    @Composable
    fun VariometerWidget(
        widgetManager: MapUIWidgetManager,
        variometerState: VariometerUiState,
        needleValue: Float,
        fastNeedleValue: Float,
        audioNeedleValue: Float,
        outerArcValue: Float? = null,
        displayValue: Float,
        displayLabel: String = String.format("%+.1f", displayValue),
        secondaryLabel: String? = null,
        secondaryLabelColor: Color? = null,
        dialConfig: VarioDialConfig = VarioDialConfig(),
        windDirectionScreenDeg: Float,
        windIsValid: Boolean,
        windSpeedLabel: String? = null,
        screenWidthPx: Float,
        screenHeightPx: Float,
        minSizePx: Float,
        maxSizePx: Float,
        isEditMode: Boolean,
        onOffsetChange: (Offset) -> Unit,
        onSizeChange: (Float) -> Unit,
        onLongPress: () -> Unit,
        onEditFinished: () -> Unit,
        modifier: Modifier = Modifier
    ) = VariometerWidgetImpl(
        widgetManager = widgetManager,
        variometerState = variometerState,
        needleValue = needleValue,
        fastNeedleValue = fastNeedleValue,
        audioNeedleValue = audioNeedleValue,
        outerArcValue = outerArcValue,
        displayValue = displayValue,
        displayLabel = displayLabel,
        secondaryLabel = secondaryLabel,
        secondaryLabelColor = secondaryLabelColor,
        dialConfig = dialConfig,
        windDirectionScreenDeg = windDirectionScreenDeg,
        windIsValid = windIsValid,
        windSpeedLabel = windSpeedLabel,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        minSizePx = minSizePx,
        maxSizePx = maxSizePx,
        isEditMode = isEditMode,
        onOffsetChange = onOffsetChange,
        onSizeChange = onSizeChange,
        onLongPress = onLongPress,
        onEditFinished = onEditFinished,
        modifier = modifier
    )

    @Composable
    fun BallastWidget(
        widgetManager: MapUIWidgetManager,
        ballastState: BallastUiState,
        onCommand: (BallastCommand) -> Unit,
        ballastOffset: Offset,
        screenWidthPx: Float,
        screenHeightPx: Float,
        onOffsetChange: (Offset) -> Unit,
        isEditMode: Boolean,
        modifier: Modifier = Modifier,
        widthDp: Float = 40f,
        heightDp: Float = 120f
    ) = BallastWidgetImpl(
        widgetManager = widgetManager,
        ballastState = ballastState,
        onCommand = onCommand,
        ballastOffset = ballastOffset,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        onOffsetChange = onOffsetChange,
        isEditMode = isEditMode,
        modifier = modifier,
        widthDp = widthDp,
        heightDp = heightDp
    )

    @Composable
    fun SideHamburgerMenu(
        widgetManager: MapUIWidgetManager,
        hamburgerOffset: Offset,
        screenWidthPx: Float,
        screenHeightPx: Float,
        sizePx: Float = 90f,
        onHamburgerTap: () -> Unit,
        onHamburgerLongPress: () -> Unit,
        onOffsetChange: (Offset) -> Unit,
        onSizeChange: (Float) -> Unit = {},
        isEditMode: Boolean,
        modifier: Modifier = Modifier
    ) = SideHamburgerMenuImpl(
        widgetManager = widgetManager,
        hamburgerOffset = hamburgerOffset,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        sizePx = sizePx,
        onHamburgerTap = onHamburgerTap,
        onHamburgerLongPress = onHamburgerLongPress,
        onOffsetChange = onOffsetChange,
        onSizeChange = onSizeChange,
        isEditMode = isEditMode,
        modifier = modifier
    )

    @Composable
    fun SettingsShortcut(
        widgetManager: MapUIWidgetManager,
        settingsOffset: Offset,
        screenWidthPx: Float,
        screenHeightPx: Float,
        sizePx: Float = 56f,
        onSettingsTap: () -> Unit,
        onOffsetChange: (Offset) -> Unit,
        onSizeChange: (Float) -> Unit = {},
        isEditMode: Boolean,
        modifier: Modifier = Modifier
    ) = SettingsShortcutWidgetImpl(
        widgetManager = widgetManager,
        settingsOffset = settingsOffset,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        sizePx = sizePx,
        onSettingsTap = onSettingsTap,
        onOffsetChange = onOffsetChange,
        onSizeChange = onSizeChange,
        isEditMode = isEditMode,
        modifier = modifier
    )

    @Composable
    fun FlightModeMenu(
        widgetManager: MapUIWidgetManager,
        currentMode: FlightMode,
        visibleModes: List<FlightMode>,
        onModeChange: (FlightMode) -> Unit,
        flightModeOffset: Offset,
        screenWidthPx: Float,
        screenHeightPx: Float,
        onOffsetChange: (Offset) -> Unit,
        isEditMode: Boolean,
        modifier: Modifier = Modifier,
        widthDp: Float = 96f,
        heightDp: Float = 36f
    ) = FlightModeMenuImpl(
        widgetManager = widgetManager,
        currentMode = currentMode,
        visibleModes = visibleModes,
        onModeChange = onModeChange,
        flightModeOffset = flightModeOffset,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        onOffsetChange = onOffsetChange,
        isEditMode = isEditMode,
        modifier = modifier,
        widthDp = widthDp,
        heightDp = heightDp
    )
}
