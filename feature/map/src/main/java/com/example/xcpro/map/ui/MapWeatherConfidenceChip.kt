package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.xcpro.weather.rain.WeatherMapConfidenceLevel
import com.example.xcpro.weather.rain.WeatherOverlayRuntimeState
import com.example.xcpro.weather.rain.resolveWeatherMapConfidenceState

internal const val WEATHER_MAP_CONFIDENCE_CHIP_TAG = "weather_map_confidence_chip"

@Composable
internal fun WeatherMapConfidenceChip(
    runtimeState: WeatherOverlayRuntimeState,
    modifier: Modifier = Modifier
) {
    val confidenceState = resolveWeatherMapConfidenceState(runtimeState)
    if (!confidenceState.visible) return

    val (containerColor, contentColor) = when (confidenceState.level) {
        WeatherMapConfidenceLevel.LIVE -> Color(0xCC166534) to Color.White
        WeatherMapConfidenceLevel.STALE -> Color(0xCC92400E) to Color.White
        WeatherMapConfidenceLevel.ERROR -> Color(0xCC991B1B) to Color.White
    }

    Surface(
        modifier = modifier.testTag(WEATHER_MAP_CONFIDENCE_CHIP_TAG),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Text(
            text = confidenceState.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}
