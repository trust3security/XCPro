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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.forecast.FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT
import com.example.xcpro.forecast.FORECAST_FOLLOW_TIME_OFFSET_OPTIONS_MINUTES
import com.example.xcpro.forecast.FORECAST_WIND_OVERLAY_SCALE_MAX
import com.example.xcpro.forecast.FORECAST_WIND_OVERLAY_SCALE_MIN
import com.example.xcpro.forecast.clampForecastOpacity
import com.example.xcpro.forecast.clampForecastWindOverlayScale
import com.example.xcpro.forecast.forecastRegionLabel
import com.example.xcpro.forecast.forecastRegionZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ForecastOverlayBottomSheet(
    uiState: ForecastOverlayUiState,
    onDismiss: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onPrimaryParameterToggled: (ForecastParameterId) -> Unit,
    onWindOverlayEnabledChanged: (Boolean) -> Unit,
    onWindParameterSelected: (ForecastParameterId) -> Unit,
    onAutoTimeEnabledChanged: (Boolean) -> Unit,
    onFollowTimeOffsetChanged: (Int) -> Unit,
    onJumpToNow: () -> Unit,
    onTimeSelected: (Long) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onWindOverlayScaleChanged: (Float) -> Unit,
    onWindDisplayModeChanged: (ForecastWindDisplayMode) -> Unit
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
        ?.let { slot ->
            formatForecastTime(
                timeUtcMs = slot.validTimeUtcMs,
                regionCode = uiState.selectedRegionCode
            )
        }
    val followOffsetOptions = FORECAST_FOLLOW_TIME_OFFSET_OPTIONS_MINUTES
    val selectedFollowOffsetIndex = remember(uiState.followTimeOffsetMinutes) {
        val fallbackIndex = followOffsetOptions.indexOf(FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT)
            .takeIf { index -> index >= 0 }
            ?: 0
        followOffsetOptions.indexOf(uiState.followTimeOffsetMinutes)
            .takeIf { index -> index >= 0 }
            ?: fallbackIndex
    }
    var followOffsetIndex by remember(uiState.followTimeOffsetMinutes, selectedFollowOffsetIndex) {
        mutableFloatStateOf(selectedFollowOffsetIndex.toFloat())
    }
    LaunchedEffect(selectedFollowOffsetIndex) {
        followOffsetIndex = selectedFollowOffsetIndex.toFloat()
    }
    val maxFollowOffsetIndex = followOffsetOptions.lastIndex.coerceAtLeast(0)
    val displayFollowOffsetIndex = followOffsetIndex
        .roundToInt()
        .coerceIn(0, maxFollowOffsetIndex)
    val displayFollowOffsetMinutes = followOffsetOptions
        .getOrNull(displayFollowOffsetIndex)
        ?: FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT
    var opacityDraft by remember { mutableFloatStateOf(uiState.opacity) }
    var windOverlayScaleDraft by remember { mutableFloatStateOf(uiState.windOverlayScale) }
    LaunchedEffect(uiState.opacity) {
        opacityDraft = uiState.opacity
    }
    LaunchedEffect(uiState.windOverlayScale) {
        windOverlayScaleDraft = uiState.windOverlayScale
    }
    val selectedPrimaryOverlayIds = remember(
        uiState.selectedPrimaryParameterId,
        uiState.secondaryPrimaryOverlayEnabled,
        uiState.selectedSecondaryPrimaryParameterId
    ) {
        buildList {
            add(uiState.selectedPrimaryParameterId)
            if (
                uiState.secondaryPrimaryOverlayEnabled &&
                !uiState.selectedSecondaryPrimaryParameterId.value.equals(
                    uiState.selectedPrimaryParameterId.value,
                    ignoreCase = true
                )
            ) {
                add(uiState.selectedSecondaryPrimaryParameterId)
            }
        }
    }
    val selectedPrimaryOverlayCount = selectedPrimaryOverlayIds.size

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
                text = "Parameters",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Select up to 2 non-wind overlays",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.primaryParameters.isEmpty()) {
                    item {
                        Text(
                            text = "No parameters available",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    items(
                        items = uiState.primaryParameters,
                        key = { parameter -> parameter.id.value }
                    ) { parameter ->
                        val isSelected = selectedPrimaryOverlayIds.any { selected ->
                            selected.value.equals(parameter.id.value, ignoreCase = true)
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { onPrimaryParameterToggled(parameter.id) },
                            label = { Text(parameter.name) },
                            enabled = uiState.enabled && (
                                isSelected || selectedPrimaryOverlayCount < MAX_PRIMARY_OVERLAY_SELECTIONS
                            )
                        )
                    }
                }
            }

            Text(
                text = "Wind overlay",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show wind",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.windOverlayEnabled,
                    onCheckedChange = onWindOverlayEnabledChanged,
                    enabled = uiState.enabled
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.windParameters.isEmpty()) {
                    item {
                        Text(
                            text = "No wind parameters available",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    items(
                        items = uiState.windParameters,
                        key = { parameter -> parameter.id.value }
                    ) { parameter ->
                        FilterChip(
                            selected = parameter.id == uiState.selectedWindParameterId,
                            onClick = { onWindParameterSelected(parameter.id) },
                            label = { Text(parameter.name) },
                            enabled = uiState.enabled && uiState.windOverlayEnabled
                        )
                    }
                }
            }

            Text(
                text = selectedTimeLabel?.let { "Time $it" } ?: "Time",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Region time zone: ${forecastRegionLabel(uiState.selectedRegionCode)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                text = "Follow time offset ${formatFollowTimeOffsetLabel(displayFollowOffsetMinutes)}",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = followOffsetIndex,
                onValueChange = { raw ->
                    followOffsetIndex = raw.coerceIn(0f, maxFollowOffsetIndex.toFloat())
                },
                onValueChangeFinished = {
                    val index = followOffsetIndex.roundToInt().coerceIn(0, maxFollowOffsetIndex)
                    val selectedOffsetMinutes = followOffsetOptions
                        .getOrNull(index)
                        ?: FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT
                    if (selectedOffsetMinutes != uiState.followTimeOffsetMinutes) {
                        onFollowTimeOffsetChanged(selectedOffsetMinutes)
                    }
                },
                enabled = uiState.enabled && uiState.autoTimeEnabled,
                valueRange = 0f..maxFollowOffsetIndex.toFloat(),
                steps = (maxFollowOffsetIndex - 1).coerceAtLeast(0)
            )

            Text(
                text = "Opacity ${(opacityDraft * 100f).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = opacityDraft,
                onValueChange = { value ->
                    opacityDraft = clampForecastOpacity(value)
                },
                onValueChangeFinished = {
                    if (opacityDraft != uiState.opacity) {
                        onOpacityChanged(opacityDraft)
                    }
                },
                enabled = uiState.enabled,
                valueRange = 0f..1f
            )

            Text(
                text = "Wind marker size ${(windOverlayScaleDraft * 100f).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Applies to wind overlays only",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = ForecastWindDisplayMode.entries,
                    key = { mode -> mode.storageValue }
                ) { mode ->
                    FilterChip(
                        selected = uiState.windDisplayMode == mode,
                        onClick = { onWindDisplayModeChanged(mode) },
                        label = { Text(mode.label) },
                        enabled = uiState.enabled && uiState.windOverlayEnabled
                    )
                }
            }
            Slider(
                value = windOverlayScaleDraft,
                onValueChange = { value ->
                    windOverlayScaleDraft = clampForecastWindOverlayScale(value)
                },
                onValueChangeFinished = {
                    if (windOverlayScaleDraft != uiState.windOverlayScale) {
                        onWindOverlayScaleChanged(windOverlayScaleDraft)
                    }
                },
                enabled = uiState.enabled && uiState.windOverlayEnabled,
                valueRange = FORECAST_WIND_OVERLAY_SCALE_MIN..FORECAST_WIND_OVERLAY_SCALE_MAX
            )

            uiState.primaryLegend?.let { legend ->
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
            if (uiState.secondaryPrimaryOverlayEnabled) {
                uiState.secondaryPrimaryLegend?.let { legend ->
                    Text(
                        text = "Second overlay legend (${legend.unitLabel})",
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
            }

            if (uiState.isLoading) {
                Text(
                    text = "Loading overlay data...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            uiState.warningMessage?.let { warning ->
                Text(
                    text = warning,
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
    regionCode: String,
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
            callout.pointValue.directionFromDeg?.let { direction ->
                Text(
                    text = "Wind from ${formatDirectionDegrees(direction)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Lat ${formatCoordinate(callout.latitude)}, Lon ${formatCoordinate(callout.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Valid ${
                    formatForecastTime(
                        timeUtcMs = callout.pointValue.validTimeUtcMs,
                        regionCode = regionCode
                    )
                }",
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

private fun formatForecastTime(timeUtcMs: Long, regionCode: String): String {
    val formatter = DateTimeFormatter.ofPattern("EEE HH:mm")
    return formatter.format(
        Instant.ofEpochMilli(timeUtcMs).atZone(forecastRegionZoneId(regionCode))
    )
}

private fun formatFollowTimeOffsetLabel(offsetMinutes: Int): String =
    when {
        offsetMinutes > 0 -> "Now +${offsetMinutes}m"
        offsetMinutes < 0 -> "Now ${offsetMinutes}m"
        else -> "Now"
    }

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.4f", value)

private fun formatDirectionDegrees(value: Double): String {
    val normalized = ((value % 360.0) + 360.0) % 360.0
    return String.format(Locale.US, "%.0f%c", normalized, '\u00B0')
}

private const val MAX_PRIMARY_OVERLAY_SELECTIONS = 2
