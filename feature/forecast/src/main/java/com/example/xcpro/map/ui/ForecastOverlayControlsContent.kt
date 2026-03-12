package com.example.xcpro.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.xcpro.forecast.FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT
import com.example.xcpro.forecast.FORECAST_FOLLOW_TIME_OFFSET_OPTIONS_MINUTES
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MIN
import com.example.xcpro.forecast.ForecastOverlayUiState
import com.example.xcpro.forecast.ForecastParameterId
import com.example.xcpro.forecast.clampSkySightSatelliteHistoryFrames
import com.example.xcpro.forecast.forecastRegionLabel
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ForecastOverlayControlsContent(
    uiState: ForecastOverlayUiState,
    onEnabledChanged: (Boolean) -> Unit,
    onPrimaryParameterToggled: (ForecastParameterId) -> Unit,
    onWindOverlayEnabledChanged: (Boolean) -> Unit,
    onWindParameterSelected: (ForecastParameterId) -> Unit,
    onAutoTimeEnabledChanged: (Boolean) -> Unit,
    onFollowTimeOffsetChanged: (Int) -> Unit,
    onJumpToNow: () -> Unit,
    onTimeSelected: (Long) -> Unit,
    onSkySightSatelliteOverlayEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteImageryEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteRadarEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteLightningEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteAnimateEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteHistoryFramesChanged: (Int) -> Unit,
    satViewEnabled: Boolean = false,
    onSatViewEnabledChanged: (Boolean) -> Unit = {},
    warningMessage: String? = uiState.warningMessage,
    errorMessage: String? = uiState.errorMessage,
    showTitle: Boolean = true,
    title: String = "Forecast overlays",
    modifier: Modifier = Modifier
) {
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
    val clampedHistoryFrames = clampSkySightSatelliteHistoryFrames(
        uiState.skySightSatelliteHistoryFrames
    )
    var satelliteHistoryFramesSlider by remember(clampedHistoryFrames) {
        mutableFloatStateOf(clampedHistoryFrames.toFloat())
    }
    LaunchedEffect(clampedHistoryFrames) {
        satelliteHistoryFramesSlider = clampedHistoryFrames.toFloat()
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showTitle) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Show non-wind overlay",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = uiState.enabled,
                onCheckedChange = onEnabledChanged
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sat View",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = satViewEnabled,
                onCheckedChange = onSatViewEnabledChanged
            )
        }
        Text(
            text = "Uses map Satellite style while enabled.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "SkySight Satellite API overlays",
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable SkySight satellite overlays",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = uiState.skySightSatelliteOverlayEnabled,
                onCheckedChange = onSkySightSatelliteOverlayEnabledChanged
            )
        }
        Text(
            text = "Cloud imagery, radar, and lightning from satellite.skysight.io.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (uiState.skySightSatelliteOverlayEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Satellite imagery (clouds)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.skySightSatelliteImageryEnabled,
                    onCheckedChange = onSkySightSatelliteImageryEnabledChanged
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rain radar",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.skySightSatelliteRadarEnabled,
                    onCheckedChange = onSkySightSatelliteRadarEnabledChanged
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lightning",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.skySightSatelliteLightningEnabled,
                    onCheckedChange = onSkySightSatelliteLightningEnabledChanged
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Animate loop",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.skySightSatelliteAnimateEnabled,
                    onCheckedChange = onSkySightSatelliteAnimateEnabledChanged
                )
            }
            Text(
                text = "History frames $clampedHistoryFrames (10-minute step)",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = satelliteHistoryFramesSlider,
                onValueChange = { raw ->
                    satelliteHistoryFramesSlider = raw.coerceIn(
                        FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MIN.toFloat(),
                        FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX.toFloat()
                    )
                },
                onValueChangeFinished = {
                    val selected = clampSkySightSatelliteHistoryFrames(
                        satelliteHistoryFramesSlider.roundToInt()
                    )
                    if (selected != uiState.skySightSatelliteHistoryFrames) {
                        onSkySightSatelliteHistoryFramesChanged(selected)
                    }
                },
                valueRange = FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MIN.toFloat()..
                    FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX.toFloat(),
                steps = (
                    FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX -
                        FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MIN - 1
                    ).coerceAtLeast(0)
            )
            Text(
                text = "Live sources typically update around 10-15 minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "Select non-wind overlay",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (uiState.primaryParameters.isEmpty()) {
            Text(
                text = "No parameters available",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.primaryParameters.forEach { parameter ->
                    val isSelected = uiState.selectedPrimaryParameterId.value.equals(
                        parameter.id.value,
                        ignoreCase = true
                    )
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPrimaryParameterToggled(parameter.id) },
                        label = { Text(parameter.name) },
                        enabled = true
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Show wind",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = uiState.windOverlayEnabled,
                onCheckedChange = onWindOverlayEnabledChanged,
                enabled = true
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
                        enabled = uiState.windOverlayEnabled
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
                enabled = true
            ) {
                Text("Now")
            }
            Switch(
                checked = uiState.autoTimeEnabled,
                onCheckedChange = onAutoTimeEnabledChanged,
                enabled = true
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
                enabled = true,
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
            enabled = uiState.autoTimeEnabled,
            valueRange = 0f..maxFollowOffsetIndex.toFloat(),
            steps = (maxFollowOffsetIndex - 1).coerceAtLeast(0)
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
        if (uiState.isLoading) {
            Text(
                text = "Loading overlay data...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        warningMessage?.let { warning ->
            Text(
                text = warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
