package com.example.dfcards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared component for selecting flight modes (Cruise, Thermal, Final Glide).
 */
@Composable
fun FlightModeSelectionSection(
    selectedFlightMode: FlightModeSelection,
    onFlightModeSelected: (FlightModeSelection) -> Unit,
    modifier: Modifier = Modifier,
    flightModeVisibilities: Map<FlightModeSelection, Boolean> = emptyMap(),
    onFlightModeVisibilityToggle: (FlightModeSelection) -> Unit = {},
    onFlightModeOptionsClick: (FlightModeSelection) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val modes = FlightModeSelection.values()
    val spacing = 6.dp
    val totalSpacing = spacing * (modes.size - 1)
    val cardWidth = (screenWidth - totalSpacing) / modes.size

    Column(modifier = modifier) {
        Text(
            text = "Flight Mode Screens",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            modes.forEach { mode ->
                val isVisible = flightModeVisibilities[mode] ?: true
                FlightModeCard(
                    mode = mode,
                    isSelected = selectedFlightMode == mode,
                    onSelect = {
                        if (mode == FlightModeSelection.CRUISE || isVisible) {
                            onFlightModeSelected(mode)
                        }
                    },
                    modifier = Modifier.width(cardWidth),
                    isVisible = isVisible,
                    onVisibilityToggle = { onFlightModeVisibilityToggle(mode) }
                )
            }
        }
    }
}

/**
 * Individual flight mode card (Cruise, Thermal, Final Glide) with visibility controls.
 */
@Composable
private fun FlightModeCard(
    mode: FlightModeSelection,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    onVisibilityToggle: () -> Unit = {}
) {
    Surface(
        onClick = onSelect,
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp)),
        enabled = isVisible || mode == FlightModeSelection.CRUISE,
        color = if (!isVisible && mode != FlightModeSelection.CRUISE) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = mode.icon,
                    contentDescription = null,
                    tint = when {
                        isSelected && (isVisible || mode == FlightModeSelection.CRUISE) ->
                            MaterialTheme.colorScheme.primary
                        !isVisible && mode != FlightModeSelection.CRUISE ->
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = when {
                        isSelected && (isVisible || mode == FlightModeSelection.CRUISE) ->
                            MaterialTheme.colorScheme.primary
                        !isVisible && mode != FlightModeSelection.CRUISE ->
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = if (mode == FlightModeSelection.CRUISE) {
                    {}
                } else {
                    onVisibilityToggle
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(24.dp)
            ) {
                Icon(
                    imageVector = if (mode == FlightModeSelection.CRUISE || isVisible) {
                        Icons.Default.Visibility
                    } else {
                        Icons.Default.VisibilityOff
                    },
                    contentDescription = when {
                        mode == FlightModeSelection.CRUISE -> "${mode.displayName} always visible"
                        isVisible -> "Hide ${mode.displayName}"
                        else -> "Show ${mode.displayName}"
                    },
                    tint = if (mode == FlightModeSelection.CRUISE || isVisible) {
                        mode.color
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
