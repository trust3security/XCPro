package com.example.xcpro.map.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.forecast.ForecastOverlayUiState
import com.example.xcpro.forecast.ForecastParameterId

internal enum class MapBottomTab(val label: String) {
    SKYSIGHT("SkySight"),
    OGN("Scia"),
    TAB_4("Tab 4")
}

internal data class OgnTrailAircraftRowUi(
    val key: String,
    val label: String,
    val trailsEnabled: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapBottomTabsLayer(
    selectedTab: MapBottomTab,
    isSheetVisible: Boolean,
    isTaskPanelVisible: Boolean,
    onTabSelected: (MapBottomTab) -> Unit,
    onDismissSheet: () -> Unit,
    onRainViewerSelected: () -> Unit,
    weatherEnabled: Boolean,
    ognEnabled: Boolean,
    showSciaEnabled: Boolean,
    onOgnEnabledChanged: (Boolean) -> Unit,
    onShowSciaEnabledChanged: (Boolean) -> Unit,
    adsbTrafficEnabled: Boolean,
    showOgnThermalsEnabled: Boolean,
    showDistanceCircles: Boolean,
    currentQnhLabel: String,
    onAdsbTrafficEnabledChanged: (Boolean) -> Unit,
    onShowOgnThermalsEnabledChanged: (Boolean) -> Unit,
    onShowDistanceCirclesChanged: (Boolean) -> Unit,
    onOpenQnhDialogFromTab: () -> Unit,
    ognTrailAircraftRows: List<OgnTrailAircraftRowUi>,
    onOgnTrailAircraftToggled: (String, Boolean) -> Unit,
    skySightUiState: ForecastOverlayUiState,
    onSkySightEnabledChanged: (Boolean) -> Unit,
    onSkySightPrimaryParameterToggled: (ForecastParameterId) -> Unit,
    onSkySightWindOverlayEnabledChanged: (Boolean) -> Unit,
    onSkySightWindParameterSelected: (ForecastParameterId) -> Unit,
    onSkySightAutoTimeEnabledChanged: (Boolean) -> Unit,
    onSkySightFollowTimeOffsetChanged: (Int) -> Unit,
    onSkySightJumpToNow: () -> Unit,
    onSkySightTimeSelected: (Long) -> Unit,
    onSkySightSatelliteOverlayEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteImageryEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteRadarEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteLightningEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteAnimateEnabledChanged: (Boolean) -> Unit,
    onSkySightSatelliteHistoryFramesChanged: (Int) -> Unit,
    skySightWarningMessage: String?,
    skySightErrorMessage: String?,
    skySightSatViewEnabled: Boolean,
    onSkySightSatViewEnabledChanged: (Boolean) -> Unit
) {
    if (!isSheetVisible && !isTaskPanelVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(65f)
        ) {
            BottomFloatingStrip(
                onTabSelected = onTabSelected,
                onRainViewerSelected = onRainViewerSelected,
                weatherEnabled = weatherEnabled,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = FLOATING_TAB_STRIP_BOTTOM_PADDING)
            )
        }
    }

    if (isSheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        MapBottomTab.SKYSIGHT -> {
                            ForecastOverlayControlsContent(
                                uiState = skySightUiState,
                                onEnabledChanged = onSkySightEnabledChanged,
                                onPrimaryParameterToggled = onSkySightPrimaryParameterToggled,
                                onWindOverlayEnabledChanged = onSkySightWindOverlayEnabledChanged,
                                onWindParameterSelected = onSkySightWindParameterSelected,
                                onAutoTimeEnabledChanged = onSkySightAutoTimeEnabledChanged,
                                onFollowTimeOffsetChanged = onSkySightFollowTimeOffsetChanged,
                                onJumpToNow = onSkySightJumpToNow,
                                onTimeSelected = onSkySightTimeSelected,
                                onSkySightSatelliteOverlayEnabledChanged = onSkySightSatelliteOverlayEnabledChanged,
                                onSkySightSatelliteImageryEnabledChanged = onSkySightSatelliteImageryEnabledChanged,
                                onSkySightSatelliteRadarEnabledChanged = onSkySightSatelliteRadarEnabledChanged,
                                onSkySightSatelliteLightningEnabledChanged = onSkySightSatelliteLightningEnabledChanged,
                                onSkySightSatelliteAnimateEnabledChanged = onSkySightSatelliteAnimateEnabledChanged,
                                onSkySightSatelliteHistoryFramesChanged = onSkySightSatelliteHistoryFramesChanged,
                                satViewEnabled = skySightSatViewEnabled,
                                onSatViewEnabledChanged = onSkySightSatViewEnabledChanged,
                                title = "SkySight",
                                warningMessage = skySightWarningMessage,
                                errorMessage = skySightErrorMessage
                            )
                        }

                        MapBottomTab.OGN -> {
                            OgnTabContent(
                                ognEnabled = ognEnabled,
                                showSciaEnabled = showSciaEnabled,
                                onOgnEnabledChanged = onOgnEnabledChanged,
                                onShowSciaEnabledChanged = onShowSciaEnabledChanged,
                                aircraftRows = ognTrailAircraftRows,
                                onAircraftTrailToggled = onOgnTrailAircraftToggled
                            )
                        }

                        MapBottomTab.TAB_4 -> {
                            Tab4ControlsContent(
                                adsbTrafficEnabled = adsbTrafficEnabled,
                                showOgnThermalsEnabled = showOgnThermalsEnabled,
                                showDistanceCircles = showDistanceCircles,
                                currentQnhLabel = currentQnhLabel,
                                onAdsbTrafficEnabledChanged = onAdsbTrafficEnabledChanged,
                                onShowOgnThermalsEnabledChanged = onShowOgnThermalsEnabledChanged,
                                onShowDistanceCirclesChanged = onShowDistanceCirclesChanged,
                                onOpenQnhDialog = onOpenQnhDialogFromTab
                            )
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
private fun BottomFloatingStrip(
    onTabSelected: (MapBottomTab) -> Unit,
    onRainViewerSelected: () -> Unit,
    weatherEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.widthIn(max = 420.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        val defaultBorderColor = MaterialTheme.colorScheme.outlineVariant
        val rainViewerBorderColor = resolveRainViewerBorderColor(
            weatherEnabled = weatherEnabled,
            defaultBorderColor = defaultBorderColor
        )
        AssistChip(
            onClick = onRainViewerSelected,
            border = BorderStroke(TAB_CHIP_BORDER_WIDTH, rainViewerBorderColor),
            label = {
                Text(
                    text = "RainViewer",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        )
        BottomTabStrip(
            onTabSelected = onTabSelected
        )
    }
}

@Composable
private fun BottomTabStrip(
    onTabSelected: (MapBottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.widthIn(max = 420.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        val borderColor = MaterialTheme.colorScheme.outlineVariant
        MapBottomTab.entries.forEach { tab ->
            AssistChip(
                onClick = { onTabSelected(tab) },
                border = BorderStroke(TAB_CHIP_BORDER_WIDTH, borderColor),
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    }
}

@Composable
private fun OgnTabContent(
    ognEnabled: Boolean,
    showSciaEnabled: Boolean,
    onOgnEnabledChanged: (Boolean) -> Unit,
    onShowSciaEnabledChanged: (Boolean) -> Unit,
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
            onCheckedChange = onOgnEnabledChanged,
            enabled = !showSciaEnabled
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Show Scia")
        Switch(
            checked = showSciaEnabled,
            onCheckedChange = onShowSciaEnabledChanged
        )
    }
    if (!ognEnabled) {
        Text(
            text = "Enable OGN traffic to manage aircraft trail visibility.",
            style = MaterialTheme.typography.bodySmall
        )
    } else if (!showSciaEnabled) {
        Text(
            text = "Enable Show Scia to display OGN trails/wake.",
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
private fun Tab4ControlsContent(
    adsbTrafficEnabled: Boolean,
    showOgnThermalsEnabled: Boolean,
    showDistanceCircles: Boolean,
    currentQnhLabel: String,
    onAdsbTrafficEnabledChanged: (Boolean) -> Unit,
    onShowOgnThermalsEnabledChanged: (Boolean) -> Unit,
    onShowDistanceCirclesChanged: (Boolean) -> Unit,
    onOpenQnhDialog: () -> Unit
) {
    Text(
        text = "Map controls",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAB4_ADSB_SWITCH_TAG)
            .toggleable(
                value = adsbTrafficEnabled,
                role = Role.Switch,
                onValueChange = onAdsbTrafficEnabledChanged
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "ADS-B traffic")
        Switch(
            checked = adsbTrafficEnabled,
            onCheckedChange = null
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAB4_THERMALS_SWITCH_TAG)
            .toggleable(
                value = showOgnThermalsEnabled,
                role = Role.Switch,
                onValueChange = onShowOgnThermalsEnabledChanged
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Hotspots (TH)")
        Switch(
            checked = showOgnThermalsEnabled,
            onCheckedChange = null
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAB4_DISTANCE_SWITCH_TAG)
            .toggleable(
                value = showDistanceCircles,
                role = Role.Switch,
                onValueChange = onShowDistanceCirclesChanged
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Distance circles")
        Switch(
            checked = showDistanceCircles,
            onCheckedChange = null
        )
    }
    Text(
        text = "QNH $currentQnhLabel",
        style = MaterialTheme.typography.bodyMedium
    )
    Button(
        onClick = onOpenQnhDialog,
        modifier = Modifier.testTag(TAB4_QNH_BUTTON_TAG)
    ) {
        Text("Set QNH")
    }
    Text(
        text = "These controls replace the map FABs for ADS-B, QNH, Hotspots and circles.",
        style = MaterialTheme.typography.bodySmall
    )
}

private val FLOATING_TAB_STRIP_BOTTOM_PADDING = 0.dp
private val SHEET_CONTENT_HORIZONTAL_PADDING = 16.dp
private val SHEET_CONTENT_TOP_PADDING = 8.dp
private val SHEET_CONTENT_BOTTOM_PADDING = 0.dp
private val SHEET_DIVIDER_TOP_PADDING = 4.dp
private val SHEET_DIVIDER_BOTTOM_PADDING = 1.dp
private val SHEET_TAB_STRIP_BOTTOM_PADDING = 0.dp
private val TAB_CHIP_BORDER_WIDTH = 1.dp
internal val RAINVIEWER_TAB_ENABLED_BORDER_COLOR = Color(0xFF16A34A)
internal const val TAB4_ADSB_SWITCH_TAG = "tab4_adsb_switch"
internal const val TAB4_THERMALS_SWITCH_TAG = "tab4_thermals_switch"
internal const val TAB4_DISTANCE_SWITCH_TAG = "tab4_distance_switch"
internal const val TAB4_QNH_BUTTON_TAG = "tab4_qnh_button"

internal fun resolveRainViewerBorderColor(
    weatherEnabled: Boolean,
    defaultBorderColor: Color
): Color {
    return if (weatherEnabled) {
        RAINVIEWER_TAB_ENABLED_BORDER_COLOR
    } else {
        defaultBorderColor
    }
}
