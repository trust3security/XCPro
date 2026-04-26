package com.trust3.xcpro.map.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.map.model.MapLocationUiModel

@Composable
fun MapActionButtons(
    currentLocation: MapLocationUiModel?,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    onRecenter: () -> Unit,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topInset = 24.dp
    val bottomInset = 80.dp
    val fabSize = 48.dp
    val fabSpacing = 16.dp
    val fabStep = fabSize + fabSpacing
    val showRecenterControl = showRecenterButton && currentLocation != null && !showReturnButton
    val centerControlsCount = (if (showRecenterControl) 1 else 0) + if (showReturnButton) 1 else 0

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset, bottom = bottomInset)
    ) {
        val availableHeight = maxHeight
        val centerGroupHeight = if (centerControlsCount == 0) 0.dp else fabSize + fabStep * (centerControlsCount - 1)
        val centerGroupTop = if (centerControlsCount == 0) {
            0.dp
        } else {
            ((availableHeight - centerGroupHeight) / 2f).coerceAtLeast(0.dp)
        }
        val recenterTopPadding = centerGroupTop
        val returnTopPadding = if (showRecenterControl) {
            centerGroupTop + fabStep
        } else {
            centerGroupTop
        }

        if (showRecenterControl) {
            RecenterButton(
                onRecenter = onRecenter,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = recenterTopPadding)
            )
        }

        if (showReturnButton) {
            ReturnButton(
                onReturn = onReturn,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = returnTopPadding, end = 16.dp)
                )
        }
    }
}
