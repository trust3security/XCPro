package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import org.maplibre.android.geometry.LatLng

internal data class MapScreenWindTapUiState(
    val tappedWindArrowCallout: WindArrowTapCallout?,
    val windTapLabelSize: IntSize,
    val overlayViewportSize: IntSize,
    val onForecastWindArrowSpeedTap: (LatLng, Double) -> Unit,
    val onWindTapLabelSizeChanged: (IntSize) -> Unit,
    val onOverlayViewportSizeChanged: (IntSize) -> Unit
)

@Composable
internal fun rememberMapScreenWindTapUiState(
    isForecastWindArrowOverlayActive: Boolean
): MapScreenWindTapUiState {
    var tappedWindArrowCallout by remember { mutableStateOf<WindArrowTapCallout?>(null) }
    var windTapLabelSize by remember { mutableStateOf(IntSize.Zero) }
    var overlayViewportSize by remember { mutableStateOf(IntSize.Zero) }

    WindArrowTapRuntimeEffects(
        isForecastWindArrowOverlayActive = isForecastWindArrowOverlayActive,
        tappedWindArrowCallout = tappedWindArrowCallout,
        onClearTapCallout = { tappedWindArrowCallout = null },
        onResetWindTapLabelSize = { windTapLabelSize = IntSize.Zero }
    )

    return MapScreenWindTapUiState(
        tappedWindArrowCallout = tappedWindArrowCallout,
        windTapLabelSize = windTapLabelSize,
        overlayViewportSize = overlayViewportSize,
        onForecastWindArrowSpeedTap = { tapLatLng, speedKt ->
            if (isForecastWindArrowOverlayActive) {
                tappedWindArrowCallout = WindArrowTapCallout(
                    tapLatLng = tapLatLng,
                    speedKt = speedKt
                )
            }
        },
        onWindTapLabelSizeChanged = { windTapLabelSize = it },
        onOverlayViewportSizeChanged = { overlayViewportSize = it }
    )
}
