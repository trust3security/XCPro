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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.xcpro.forecast.ForecastOverlayUiState
import com.example.xcpro.forecast.ForecastParameterId
import com.example.xcpro.forecast.ForecastPointCallout
import com.example.xcpro.forecast.FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT
import com.example.xcpro.forecast.FORECAST_FOLLOW_TIME_OFFSET_OPTIONS_MINUTES
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MIN
import com.example.xcpro.forecast.clampSkySightSatelliteHistoryFrames
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
    onSkySightSatelliteOverlayEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteImageryEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteRadarEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteLightningEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteAnimateEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteHistoryFramesChanged: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        ForecastOverlayControlsContent(
            uiState = uiState,
            onEnabledChanged = onEnabledChanged,
            onPrimaryParameterToggled = onPrimaryParameterToggled,
            onWindOverlayEnabledChanged = onWindOverlayEnabledChanged,
            onWindParameterSelected = onWindParameterSelected,
            onAutoTimeEnabledChanged = onAutoTimeEnabledChanged,
            onFollowTimeOffsetChanged = onFollowTimeOffsetChanged,
            onJumpToNow = onJumpToNow,
            onTimeSelected = onTimeSelected,
            onSkySightSatelliteOverlayEnabledChanged = onSkySightSatelliteOverlayEnabledChanged,
            onSkySightSatelliteImageryEnabledChanged = onSkySightSatelliteImageryEnabledChanged,
            onSkySightSatelliteRadarEnabledChanged = onSkySightSatelliteRadarEnabledChanged,
            onSkySightSatelliteLightningEnabledChanged = onSkySightSatelliteLightningEnabledChanged,
            onSkySightSatelliteAnimateEnabledChanged = onSkySightSatelliteAnimateEnabledChanged,
            onSkySightSatelliteHistoryFramesChanged = onSkySightSatelliteHistoryFramesChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        )
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

internal fun formatForecastTime(timeUtcMs: Long, regionCode: String): String {
    val formatter = DateTimeFormatter.ofPattern("EEE HH:mm")
    return formatter.format(
        Instant.ofEpochMilli(timeUtcMs).atZone(forecastRegionZoneId(regionCode))
    )
}

internal fun formatFollowTimeOffsetLabel(offsetMinutes: Int): String =
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
