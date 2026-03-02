package com.example.xcpro.screens.navdrawer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.xcpro.weather.rain.WEATHER_RAIN_OPACITY_DEFAULT
import com.example.xcpro.weather.rain.WEATHER_RAIN_OPACITY_MAX
import com.example.xcpro.weather.rain.WEATHER_RAIN_OPACITY_MIN
import com.example.xcpro.weather.rain.WeatherOverlayViewModel
import com.example.xcpro.weather.rain.WeatherRainAnimationSpeed
import com.example.xcpro.weather.rain.WeatherRainAnimationWindow
import com.example.xcpro.weather.rain.WeatherRainTransitionQuality
import com.example.xcpro.weather.rain.WeatherRadarFrameMode
import com.example.xcpro.weather.rain.WeatherOverlayRuntimeState
import com.example.xcpro.weather.rain.weatherRadarStatusLabel
import com.example.xcpro.weather.rain.WEATHER_RAIN_ATTRIBUTION_LINK_URL
import com.example.xcpro.weather.rain.maxSelectableFrameCount
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch


/**
 * Weather settings support composables/helpers extracted for global file-size compliance.
 */

@Composable
internal fun WeatherSettingsCard(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

internal fun animationSpeedLabel(speed: WeatherRainAnimationSpeed): String =
    when (speed) {
        WeatherRainAnimationSpeed.SLOW -> "Slow"
        WeatherRainAnimationSpeed.NORMAL -> "Normal"
        WeatherRainAnimationSpeed.FAST -> "Fast"
    }

internal fun animationWindowLabel(window: WeatherRainAnimationWindow): String =
    "${window.windowMinutes} min"

internal fun transitionQualityLabel(quality: WeatherRainTransitionQuality): String =
    when (quality) {
        WeatherRainTransitionQuality.CRISP -> "Crisp"
        WeatherRainTransitionQuality.BALANCED -> "Balanced"
        WeatherRainTransitionQuality.SMOOTH -> "Smooth"
    }

internal fun frameModeLabel(mode: WeatherRadarFrameMode): String =
    when (mode) {
        WeatherRadarFrameMode.LATEST -> "Latest"
        WeatherRadarFrameMode.MANUAL -> "Manual"
    }

internal fun weatherAnimationWindowSummaryLabel(window: WeatherRainAnimationWindow): String {
    val maxFrames = window.maxSelectableFrameCount()
    return "${animationWindowLabel(window)} window - up to $maxFrames frames"
}

internal fun isFrameSourceControlEnabled(
    rainOverlayEnabled: Boolean,
    animatePastWindow: Boolean
): Boolean = rainOverlayEnabled && !animatePastWindow

internal fun shouldShowManualFrameControls(
    rainOverlayEnabled: Boolean,
    animatePastWindow: Boolean,
    frameMode: WeatherRadarFrameMode,
    availableFrameCount: Int
): Boolean = isFrameSourceControlEnabled(
    rainOverlayEnabled = rainOverlayEnabled,
    animatePastWindow = animatePastWindow
) && frameMode == WeatherRadarFrameMode.MANUAL && availableFrameCount > 0

internal fun weatherMetadataStatusLine(
    overlayState: WeatherOverlayRuntimeState
): String = "Metadata: ${weatherRadarStatusLabel(overlayState.metadataStatus)}"

internal fun weatherFreshnessLine(
    overlayState: WeatherOverlayRuntimeState
): String {
    val freshnessLabel = overlayState.metadataFreshnessAgeMs?.let(::formatDurationAge) ?: "n/a"
    val freshnessState = if (overlayState.metadataStale) "Stale" else "Live"
    return "Last update age: $freshnessLabel ($freshnessState)"
}

internal fun weatherContentAgeLine(
    overlayState: WeatherOverlayRuntimeState
): String {
    val contentAgeLabel = overlayState.metadataContentAgeMs?.let(::formatDurationAge) ?: "n/a"
    return "Content age: $contentAgeLabel"
}

internal fun weatherVisibleFrameAgeLine(
    overlayState: WeatherOverlayRuntimeState
): String {
    val frameAgeLabel = overlayState.selectedFrameAgeMs?.let(::formatDurationAge) ?: "n/a"
    return "Visible frame age: $frameAgeLabel"
}

internal fun formatDurationAge(ageMs: Long): String {
    val clampedAgeMs = ageMs.coerceAtLeast(0L)
    val totalSeconds = clampedAgeMs / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

internal val frameTimeFormatterUtc: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm 'UTC'", Locale.US)

internal fun formatFrameTimeUtc(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneOffset.UTC)
        .format(frameTimeFormatterUtc)

internal fun openWeatherRainAttributionLink(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WEATHER_RAIN_ATTRIBUTION_LINK_URL)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

