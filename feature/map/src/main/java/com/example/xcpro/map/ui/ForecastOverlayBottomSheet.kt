package com.example.xcpro.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.xcpro.forecast.ForecastOverlayUiState
import com.example.xcpro.forecast.ForecastParameterId
import com.example.xcpro.forecast.ForecastPointCallout
import com.example.xcpro.forecast.clampForecastOpacity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ForecastOverlayBottomSheet(
    uiState: ForecastOverlayUiState,
    onDismiss: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onParameterSelected: (ForecastParameterId) -> Unit,
    onAutoTimeEnabledChanged: (Boolean) -> Unit,
    onJumpToNow: () -> Unit,
    onTimeSelected: (Long) -> Unit,
    onOpacityChanged: (Float) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedTimeIndex = remember(uiState.timeSlots, uiState.selectedTimeUtcMs) {
        val selectedTime = uiState.selectedTimeUtcMs
        val index = uiState.timeSlots.indexOfFirst { it.validTimeUtcMs == selectedTime }
        if (index >= 0) index else 0
    }
    var sliderIndex by remember(uiState.timeSlots, selectedTimeIndex) {
        mutableFloatStateOf(selectedTimeIndex.toFloat())
    }
    LaunchedEffect(selectedTimeIndex) {
        sliderIndex = selectedTimeIndex.toFloat()
    }
    val maxTimeIndex = uiState.timeSlots.lastIndex.coerceAtLeast(0)
    val displayTimeIndex = sliderIndex.roundToInt().coerceIn(0, maxTimeIndex)
    val selectedTimeLabel = uiState.timeSlots
        .getOrNull(displayTimeIndex)
        ?.let { slot -> formatForecastTime(slot.validTimeUtcMs) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Forecast overlays",
                style = MaterialTheme.typography.titleLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enabled",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.enabled,
                    onCheckedChange = onEnabledChanged
                )
            }

            Text(
                text = "Parameter",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.parameters.isEmpty()) {
                    Text(
                        text = "No parameters available",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    uiState.parameters.forEach { parameter ->
                        FilterChip(
                            selected = parameter.id == uiState.selectedParameterId,
                            onClick = { onParameterSelected(parameter.id) },
                            label = { Text(parameter.name) },
                            enabled = uiState.enabled
                        )
                    }
                }
            }

            Text(
                text = selectedTimeLabel?.let { "Time $it" } ?: "Time",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Follow current time",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onJumpToNow,
                    enabled = uiState.enabled
                ) {
                    Text("Now")
                }
                Switch(
                    checked = uiState.autoTimeEnabled,
                    onCheckedChange = onAutoTimeEnabledChanged,
                    enabled = uiState.enabled
                )
            }
            if (uiState.timeSlots.isEmpty()) {
                Text(
                    text = "No time slots available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Slider(
                    value = sliderIndex,
                    onValueChange = { raw ->
                        sliderIndex = raw.coerceIn(0f, maxTimeIndex.toFloat())
                    },
                    onValueChangeFinished = {
                        val index = sliderIndex.roundToInt().coerceIn(0, maxTimeIndex)
                        if (uiState.autoTimeEnabled) {
                            onAutoTimeEnabledChanged(false)
                        }
                        onTimeSelected(uiState.timeSlots[index].validTimeUtcMs)
                    },
                    enabled = uiState.enabled,
                    valueRange = 0f..maxTimeIndex.toFloat(),
                    steps = (maxTimeIndex - 1).coerceAtLeast(0)
                )
                selectedTimeLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "Opacity ${(uiState.opacity * 100f).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = uiState.opacity,
                onValueChange = { value ->
                    onOpacityChanged(clampForecastOpacity(value))
                },
                enabled = uiState.enabled,
                valueRange = 0f..1f
            )

            uiState.legend?.let { legend ->
                Text(
                    text = "Legend (${legend.unitLabel})",
                    style = MaterialTheme.typography.titleMedium
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    legend.stops.forEach { stop ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 20.dp, height = 12.dp)
                                    .background(
                                        color = Color(stop.argb),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                            Text(
                                text = "${stop.value}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                Text(
                    text = "Loading overlay data...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun ForecastPointCalloutCard(
    callout: ForecastPointCallout,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Value here: ${callout.pointValue.value} ${callout.pointValue.unitLabel}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Lat ${formatCoordinate(callout.latitude)}, Lon ${formatCoordinate(callout.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Valid ${formatForecastTime(callout.pointValue.validTimeUtcMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onDismiss
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
internal fun ForecastQueryStatusChip(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AssistChip(
        modifier = modifier,
        onClick = onDismiss,
        label = { Text(message) }
    )
}

private fun formatForecastTime(timeUtcMs: Long): String {
    val formatter = DateTimeFormatter.ofPattern("EEE HH:mm")
    return formatter.format(
        Instant.ofEpochMilli(timeUtcMs).atZone(ZoneId.systemDefault())
    )
}

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.4f", value)
