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

internal const val WEATHER_SETTINGS_SHEET_TAG = "weather_settings_sheet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val scope = rememberCoroutineScope()
    WeatherSettingsSheet(
        onDismissRequest = { navController.navigateUp() },
        onNavigateUp = { navController.navigateUp() },
        onSecondaryNavigate = {
            scope.launch {
                navController.popBackStack("map", inclusive = false)
                drawerState.open()
            }
        },
        onNavigateToMap = {
            scope.launch {
                drawerState.close()
                navController.popBackStack("map", inclusive = false)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeatherSettingsSheet(
    onDismissRequest: () -> Unit,
    onNavigateUp: (() -> Unit)?,
    onSecondaryNavigate: (() -> Unit)?,
    onNavigateToMap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = { WeatherSettingsContent() }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag(WEATHER_SETTINGS_SHEET_TAG)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            SettingsTopAppBar(
                title = "RainViewer",
                onNavigateUp = onNavigateUp,
                onSecondaryNavigate = onSecondaryNavigate,
                onNavigateToMap = onNavigateToMap
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun WeatherSettingsContent(
    viewModel: WeatherSettingsViewModel = hiltViewModel(),
    overlayViewModel: WeatherOverlayViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val rainOverlayEnabled by viewModel.overlayEnabled.collectAsStateWithLifecycle()
    val rainOpacity by viewModel.opacity.collectAsStateWithLifecycle()
    val animatePastWindow by viewModel.animatePastWindow.collectAsStateWithLifecycle()
    val animationWindow by viewModel.animationWindow.collectAsStateWithLifecycle()
    val animationSpeed by viewModel.animationSpeed.collectAsStateWithLifecycle()
    val transitionQuality by viewModel.transitionQuality.collectAsStateWithLifecycle()
    val frameMode by viewModel.frameMode.collectAsStateWithLifecycle()
    val manualFrameIndex by viewModel.manualFrameIndex.collectAsStateWithLifecycle()
    val smoothEnabled by viewModel.smoothEnabled.collectAsStateWithLifecycle()
    val snowEnabled by viewModel.snowEnabled.collectAsStateWithLifecycle()
    val overlayState by overlayViewModel.overlayState.collectAsStateWithLifecycle()
    val animationWindows = viewModel.animationWindows
    val selectedFrameEpochSec = overlayState.selectedFrame?.frameTimeEpochSec
    val currentFrameLabel = selectedFrameEpochSec?.let(::formatFrameTimeUtc) ?: "No frame"
    val selectedAnimationWindowIndex = animationWindows
        .indexOf(animationWindow)
        .coerceAtLeast(0)
    val maxManualFrameIndex = (overlayState.availableFrameCount - 1).coerceAtLeast(0)
    val frameSourceControlEnabled = isFrameSourceControlEnabled(
        rainOverlayEnabled = rainOverlayEnabled,
        animatePastWindow = animatePastWindow
    )
    val showManualFrameControls = shouldShowManualFrameControls(
        rainOverlayEnabled = rainOverlayEnabled,
        animatePastWindow = animatePastWindow,
        frameMode = frameMode,
        availableFrameCount = overlayState.availableFrameCount
    )
    var rainSliderValue by remember { mutableStateOf(WEATHER_RAIN_OPACITY_DEFAULT) }
    var animationWindowSliderValue by remember { mutableStateOf(0f) }
    var manualFrameSliderValue by remember { mutableStateOf(0f) }

    LaunchedEffect(rainOpacity) {
        rainSliderValue = rainOpacity
    }
    LaunchedEffect(selectedAnimationWindowIndex) {
        animationWindowSliderValue = selectedAnimationWindowIndex.toFloat()
    }
    LaunchedEffect(manualFrameIndex, maxManualFrameIndex) {
        manualFrameSliderValue = manualFrameIndex.coerceIn(0, maxManualFrameIndex).toFloat()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WeatherSettingsCard(
            title = "Rain Overlay",
            description = "Enable rain radar tiles and tune opacity."
        ) {
            Text(
                text = "Opacity: ${(rainSliderValue * 100f).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = rainSliderValue,
                onValueChange = { value ->
                    rainSliderValue = value.coerceIn(
                        WEATHER_RAIN_OPACITY_MIN,
                        WEATHER_RAIN_OPACITY_MAX
                    )
                },
                onValueChangeFinished = {
                    val clamped = rainSliderValue.coerceIn(
                        WEATHER_RAIN_OPACITY_MIN,
                        WEATHER_RAIN_OPACITY_MAX
                    )
                    if (clamped != rainOpacity) {
                        viewModel.setOpacity(clamped)
                    }
                },
                valueRange = WEATHER_RAIN_OPACITY_MIN..WEATHER_RAIN_OPACITY_MAX
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rain overlay enabled",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = rainOverlayEnabled,
                    onCheckedChange = viewModel::setOverlayEnabled
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cycle past radar window",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = animatePastWindow,
                    onCheckedChange = viewModel::setAnimatePastWindow
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Animates recent radar frames to show rain movement speed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Animation window",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = weatherAnimationWindowSummaryLabel(animationWindow),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = animationWindowSliderValue,
                onValueChange = { value ->
                    animationWindowSliderValue = value.coerceIn(
                        0f,
                        animationWindows.lastIndex.toFloat()
                    )
                },
                onValueChangeFinished = {
                    val selectedIndex = animationWindowSliderValue.roundToInt()
                        .coerceIn(0, animationWindows.lastIndex)
                    val selectedWindow = animationWindows[selectedIndex]
                    if (selectedWindow != animationWindow) {
                        viewModel.setAnimationWindow(selectedWindow)
                    }
                },
                enabled = animatePastWindow,
                valueRange = 0f..animationWindows.lastIndex.toFloat(),
                steps = (animationWindows.size - 2).coerceAtLeast(0)
            )
            Text(
                text = "Cycle mode always uses all eligible frames in the selected window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Animation speed",
                style = MaterialTheme.typography.bodyMedium
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = viewModel.animationSpeeds,
                    key = { speed -> speed.storageKey }
                ) { speed ->
                    FilterChip(
                        selected = speed == animationSpeed,
                        onClick = { viewModel.setAnimationSpeed(speed) },
                        label = { Text(animationSpeedLabel(speed)) },
                        enabled = animatePastWindow
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Transition quality",
                style = MaterialTheme.typography.bodyMedium
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = viewModel.transitionQualities,
                    key = { quality -> quality.storageKey }
                ) { quality ->
                    FilterChip(
                        selected = quality == transitionQuality,
                        onClick = { viewModel.setTransitionQuality(quality) },
                        label = { Text(transitionQualityLabel(quality)) },
                        enabled = rainOverlayEnabled
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Frame source",
                style = MaterialTheme.typography.bodyMedium
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = viewModel.frameModes,
                    key = { mode -> mode.storageKey }
                ) { mode ->
                    FilterChip(
                        selected = mode == frameMode,
                        onClick = { viewModel.setFrameMode(mode) },
                        label = { Text(frameModeLabel(mode)) },
                        enabled = frameSourceControlEnabled
                    )
                }
            }
            if (animatePastWindow) {
                Text(
                    text = "Manual frame selection applies when cycle mode is off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showManualFrameControls) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Manual frame",
                    style = MaterialTheme.typography.bodyMedium
                )
                val manualFrameLabel =
                    "${manualFrameIndex.coerceIn(0, maxManualFrameIndex) + 1} / ${overlayState.availableFrameCount}"
                Text(
                    text = manualFrameLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = manualFrameSliderValue,
                    onValueChange = { value ->
                        manualFrameSliderValue = value.coerceIn(0f, maxManualFrameIndex.toFloat())
                    },
                    onValueChangeFinished = {
                        val selectedIndex = manualFrameSliderValue.roundToInt()
                            .coerceIn(0, maxManualFrameIndex)
                        if (selectedIndex != manualFrameIndex) {
                            viewModel.setManualFrameIndex(selectedIndex)
                        }
                    },
                    valueRange = 0f..maxManualFrameIndex.toFloat(),
                    steps = (maxManualFrameIndex - 1).coerceAtLeast(0)
                )
            } else if (
                frameSourceControlEnabled &&
                frameMode == WeatherRadarFrameMode.MANUAL &&
                overlayState.availableFrameCount <= 0
            ) {
                Text(
                    text = "Manual frame becomes available when radar frames are loaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Render options",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Smooth interpolation",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = smoothEnabled,
                    onCheckedChange = viewModel::setSmoothEnabled,
                    enabled = rainOverlayEnabled
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show snow layer",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = snowEnabled,
                    onCheckedChange = viewModel::setSnowEnabled,
                    enabled = rainOverlayEnabled
                )
            }
            Text(
                text = "Current frame: $currentFrameLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = weatherMetadataStatusLine(overlayState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = weatherFreshnessLine(overlayState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = weatherContentAgeLine(overlayState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = weatherVisibleFrameAgeLine(overlayState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            overlayState.metadataDetail?.takeIf { detail -> detail.isNotBlank() }?.let { detail ->
                Text(
                    text = "Detail: $detail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Blend duration: ${overlayState.transitionDurationMs} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Source attribution",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Open the radar source link to verify attribution at any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = { openWeatherRainAttributionLink(context) }
            ) {
                Text("Open radar source link")
            }
            Text(
                text = WEATHER_RAIN_ATTRIBUTION_LINK_URL,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun WeatherSettingsCard(
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

private fun animationSpeedLabel(speed: WeatherRainAnimationSpeed): String =
    when (speed) {
        WeatherRainAnimationSpeed.SLOW -> "Slow"
        WeatherRainAnimationSpeed.NORMAL -> "Normal"
        WeatherRainAnimationSpeed.FAST -> "Fast"
    }

private fun animationWindowLabel(window: WeatherRainAnimationWindow): String =
    "${window.windowMinutes} min"

private fun transitionQualityLabel(quality: WeatherRainTransitionQuality): String =
    when (quality) {
        WeatherRainTransitionQuality.CRISP -> "Crisp"
        WeatherRainTransitionQuality.BALANCED -> "Balanced"
        WeatherRainTransitionQuality.SMOOTH -> "Smooth"
    }

private fun frameModeLabel(mode: WeatherRadarFrameMode): String =
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

private fun formatDurationAge(ageMs: Long): String {
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

private val frameTimeFormatterUtc: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm 'UTC'", Locale.US)

private fun formatFrameTimeUtc(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneOffset.UTC)
        .format(frameTimeFormatterUtc)

internal fun openWeatherRainAttributionLink(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WEATHER_RAIN_ATTRIBUTION_LINK_URL)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

