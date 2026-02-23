package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.forecast.ForecastParameterId
import com.example.xcpro.weather.rain.WEATHER_RAIN_ATTRIBUTION_LINK_URL
import com.example.xcpro.weather.rain.WEATHER_RAIN_OPACITY_MAX
import com.example.xcpro.weather.rain.WEATHER_RAIN_OPACITY_MIN
import kotlinx.coroutines.launch

internal enum class MapBottomTab(val label: String) {
    WEATHER("Weather"),
    SKYSIGHT("SkySight"),
    OGN("Scia"),
    TAB_4("Tab 4")
}

internal data class OgnTrailAircraftRowUi(
    val key: String,
    val label: String,
    val trailsEnabled: Boolean
)

private val THERMAL_TOPS_ID = ForecastParameterId("dwcrit")
private val CONVERGENCE_ID = ForecastParameterId("wblmaxmin")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapBottomTabsLayer(
    selectedTab: MapBottomTab,
    isSheetVisible: Boolean,
    isTaskPanelVisible: Boolean,
    onTabSelected: (MapBottomTab) -> Unit,
    onDismissSheet: () -> Unit,
    weatherEnabled: Boolean,
    weatherOpacity: Float,
    onWeatherEnabledChanged: (Boolean) -> Unit,
    onWeatherOpacityChanged: (Float) -> Unit,
    isDrawerBlocked: Boolean,
    onOpenWeatherSettingsFromTab: () -> Unit,
    ognEnabled: Boolean,
    onOgnEnabledChanged: (Boolean) -> Unit,
    ognTrailAircraftRows: List<OgnTrailAircraftRowUi>,
    onOgnTrailAircraftToggled: (String, Boolean) -> Unit,
    showSkySightPrimaryEnabled: Boolean,
    selectedPrimarySkySightIds: Set<ForecastParameterId>,
    onShowSkySightPrimaryChanged: (Boolean) -> Unit,
    onSkySightParameterToggle: (ForecastParameterId) -> Unit
) {
    if (!isSheetVisible && !isTaskPanelVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(65f)
        ) {
            BottomTabStrip(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = FLOATING_TAB_STRIP_BOTTOM_PADDING)
            )
        }
    }

    if (isSheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val uriHandler = LocalUriHandler.current

        ModalBottomSheet(
            onDismissRequest = onDismissSheet,
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        PaddingValues(
                            start = SHEET_CONTENT_HORIZONTAL_PADDING,
                            top = SHEET_CONTENT_TOP_PADDING,
                            end = SHEET_CONTENT_HORIZONTAL_PADDING,
                            bottom = SHEET_CONTENT_BOTTOM_PADDING
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedTab) {
                        MapBottomTab.WEATHER -> {
                            WeatherTabContent(
                                enabled = weatherEnabled,
                                opacity = weatherOpacity,
                                onEnabledChanged = onWeatherEnabledChanged,
                                onOpacityChanged = onWeatherOpacityChanged,
                                isDrawerBlocked = isDrawerBlocked,
                                onOpenAttribution = {
                                    uriHandler.openUri(WEATHER_RAIN_ATTRIBUTION_LINK_URL)
                                },
                                onMoreWeatherSettings = {
                                    if (isDrawerBlocked) return@WeatherTabContent
                                    scope.launch {
                                        sheetState.hide()
                                        onDismissSheet()
                                        onOpenWeatherSettingsFromTab()
                                    }
                                }
                            )
                        }

                        MapBottomTab.SKYSIGHT -> {
                            SkySightTabContent(
                                showPrimaryEnabled = showSkySightPrimaryEnabled,
                                selectedIds = selectedPrimarySkySightIds,
                                onShowPrimaryEnabledChanged = onShowSkySightPrimaryChanged,
                                onParameterToggle = onSkySightParameterToggle
                            )
                        }

                        MapBottomTab.OGN -> {
                            OgnTabContent(
                                ognEnabled = ognEnabled,
                                onOgnEnabledChanged = onOgnEnabledChanged,
                                aircraftRows = ognTrailAircraftRows,
                                onAircraftTrailToggled = onOgnTrailAircraftToggled
                            )
                        }

                        MapBottomTab.TAB_4 -> {
                            PlaceholderTabContent(text = "Tab 4 options coming soon")
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(
                        top = SHEET_DIVIDER_TOP_PADDING,
                        bottom = SHEET_DIVIDER_BOTTOM_PADDING
                    )
                )

                BottomTabStrip(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = SHEET_TAB_STRIP_BOTTOM_PADDING)
                )
            }
        }
    }
}

@Composable
private fun BottomTabStrip(
    selectedTab: MapBottomTab,
    onTabSelected: (MapBottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.widthIn(max = 420.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        MapBottomTab.entries.forEach { tab ->
            AssistChip(
                onClick = { onTabSelected(tab) },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (tab == selectedTab) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

@Composable
private fun WeatherTabContent(
    enabled: Boolean,
    opacity: Float,
    onEnabledChanged: (Boolean) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    isDrawerBlocked: Boolean,
    onOpenAttribution: () -> Unit,
    onMoreWeatherSettings: () -> Unit
) {
    Text(
        text = "Weather",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Show rain overlay")
        Switch(checked = enabled, onCheckedChange = onEnabledChanged)
    }
    Text(text = "Opacity ${(opacity * 100f).toInt()}%")
    Slider(
        value = opacity,
        onValueChange = onOpacityChanged,
        valueRange = WEATHER_RAIN_OPACITY_MIN..WEATHER_RAIN_OPACITY_MAX
    )
    Button(onClick = onOpenAttribution) {
        Text("Source Attribution")
    }
    Button(
        onClick = onMoreWeatherSettings,
        enabled = !isDrawerBlocked
    ) {
        Text("More Weather Settings")
    }
    if (isDrawerBlocked) {
        Text(
            text = "Unavailable while task edit mode is active.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
    Text(
        text = "Open drawer -> Settings -> General -> RainViewer",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun SkySightTabContent(
    showPrimaryEnabled: Boolean,
    selectedIds: Set<ForecastParameterId>,
    onShowPrimaryEnabledChanged: (Boolean) -> Unit,
    onParameterToggle: (ForecastParameterId) -> Unit
) {
    Text(
        text = "SkySight",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Show non-wind overlays")
        Switch(
            checked = showPrimaryEnabled,
            onCheckedChange = onShowPrimaryEnabledChanged
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedIds.contains(THERMAL_TOPS_ID),
            onClick = { onParameterToggle(THERMAL_TOPS_ID) },
            enabled = showPrimaryEnabled,
            label = { Text("Thermal Tops") }
        )
        FilterChip(
            selected = selectedIds.contains(CONVERGENCE_ID),
            onClick = { onParameterToggle(CONVERGENCE_ID) },
            enabled = showPrimaryEnabled,
            label = { Text("Convergence") }
        )
    }
    FilterChip(
        selected = false,
        onClick = {},
        enabled = false,
        label = { Text("Satellite View") }
    )
    Text(
        text = "Satellite View is coming soon.",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun OgnTabContent(
    ognEnabled: Boolean,
    onOgnEnabledChanged: (Boolean) -> Unit,
    aircraftRows: List<OgnTrailAircraftRowUi>,
    onAircraftTrailToggled: (String, Boolean) -> Unit
) {
    Text(
        text = "Scia (trail/wake)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "OGN Traffic")
        Switch(
            checked = ognEnabled,
            onCheckedChange = onOgnEnabledChanged
        )
    }
    if (!ognEnabled) {
        Text(
            text = "Enable OGN traffic to manage aircraft trail visibility.",
            style = MaterialTheme.typography.bodySmall
        )
    } else if (aircraftRows.isEmpty()) {
        Text(
            text = "No OGN aircraft currently available.",
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        Text(
            text = "Aircraft trail visibility",
            style = MaterialTheme.typography.labelLarge
        )
        aircraftRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = row.trailsEnabled,
                    onCheckedChange = { enabled ->
                        onAircraftTrailToggled(row.key, enabled)
                    },
                    enabled = ognEnabled
                )
            }
        }
    }
}

@Composable
private fun PlaceholderTabContent(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private val FLOATING_TAB_STRIP_BOTTOM_PADDING = 0.dp
private val SHEET_CONTENT_HORIZONTAL_PADDING = 16.dp
private val SHEET_CONTENT_TOP_PADDING = 8.dp
private val SHEET_CONTENT_BOTTOM_PADDING = 0.dp
private val SHEET_DIVIDER_TOP_PADDING = 4.dp
private val SHEET_DIVIDER_BOTTOM_PADDING = 1.dp
private val SHEET_TAB_STRIP_BOTTOM_PADDING = 0.dp
