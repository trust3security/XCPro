package com.example.xcpro.map.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.variometer.layout.VariometerUiState

/**
 * Thin wrapper object to keep existing call sites stable while the implementations
 * live in smaller, focused files. All functions delegate to the extracted contents.
 */
object MapUIWidgets {

    @Composable
    fun VariometerWidget(
        widgetManager: MapUIWidgetManager,
        variometerState: VariometerUiState,
        needleValue: Float,
        displayValue: Float,
        displayLabel: String = String.format("%+.1f", displayValue),
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
    ) = VariometerWidgetContent(
        widgetManager = widgetManager,
        variometerState = variometerState,
        needleValue = needleValue,
        displayValue = displayValue,
        displayLabel = displayLabel,
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
    ) = BallastWidgetContent(
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
        onHamburgerTap: () -> Unit,
        onHamburgerLongPress: () -> Unit,
        onOffsetChange: (Offset) -> Unit,
        isEditMode: Boolean,
        modifier: Modifier = Modifier,
        sizeDp: Float = 90f
    ) = SideHamburgerMenuContent(
        widgetManager = widgetManager,
        hamburgerOffset = hamburgerOffset,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        onHamburgerTap = onHamburgerTap,
        onHamburgerLongPress = onHamburgerLongPress,
        onOffsetChange = onOffsetChange,
        isEditMode = isEditMode,
        modifier = modifier,
        sizeDp = sizeDp
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
    ) = FlightModeMenuContent(
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
