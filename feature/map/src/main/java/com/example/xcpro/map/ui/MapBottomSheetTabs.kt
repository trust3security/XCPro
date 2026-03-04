package com.example.xcpro.map.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.forecast.ForecastOverlayUiState
import com.example.xcpro.forecast.ForecastParameterId
import com.example.xcpro.map.R
import com.example.xcpro.weather.ui.WeatherSettingsContentHost

internal enum class MapBottomTab(
    @StringRes val labelResId: Int,
    val chipTestTag: String
) {
    RAIN(
        labelResId = R.string.map_bottom_tab_rainviewer,
        chipTestTag = "map_bottom_tab_rain"
    ),
    SKYSIGHT(
        labelResId = R.string.map_bottom_tab_skysight,
        chipTestTag = "map_bottom_tab_skysight"
    ),
    OGN(
        labelResId = R.string.map_bottom_tab_scia,
        chipTestTag = "map_bottom_tab_scia"
    ),
    MAP4(
        labelResId = R.string.map_bottom_tab_map4,
        chipTestTag = "map_bottom_tab_map4"
    )
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
    onSkySightSatViewEnabledChanged: (Boolean) -> Unit,
    rainTabContent: @Composable () -> Unit = {
        RainViewerTabContent()
    }
) {
    if (!isSheetVisible && !isTaskPanelVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(65f)
        ) {
            BottomFloatingStrip(
                onTabSelected = onTabSelected,
                selectedTab = selectedTab,
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
                        MapBottomTab.RAIN -> rainTabContent()

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
                                title = stringResource(R.string.map_bottom_tab_skysight),
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

                        MapBottomTab.MAP4 -> {
                            Map4ControlsContent(
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
                    selectedTab = selectedTab,
                    weatherEnabled = weatherEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = SHEET_TAB_STRIP_BOTTOM_PADDING)
                )
            }
        }
    }
}

@Composable
private fun RainViewerTabContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.map_bottom_tab_rainviewer),
            style = MaterialTheme.typography.titleLarge
        )
        WeatherSettingsContentHost(
            enableScroll = false,
            flatSectionStyle = true,
            showSectionHeader = false
        )
    }
}

@Composable
private fun BottomFloatingStrip(
    onTabSelected: (MapBottomTab) -> Unit,
    selectedTab: MapBottomTab,
    weatherEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    BottomTabStrip(
        onTabSelected = onTabSelected,
        selectedTab = selectedTab,
        weatherEnabled = weatherEnabled,
        modifier = modifier
    )
}

@Composable
private fun BottomTabStrip(
    onTabSelected: (MapBottomTab) -> Unit,
    selectedTab: MapBottomTab,
    weatherEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .testTag(MAP_BOTTOM_TAB_STRIP_TAG),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        val defaultBorderColor = MaterialTheme.colorScheme.outlineVariant
        val selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_TAB_BORDER_ALPHA)
        MapBottomTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val borderColor = resolveTabBorderColor(
                tab = tab,
                isSelected = isSelected,
                weatherEnabled = weatherEnabled,
                defaultBorderColor = defaultBorderColor,
                selectedBorderColor = selectedBorderColor
            )
            AssistChip(
                modifier = Modifier
                    .testTag(tab.chipTestTag)
                    .semantics {
                        selected = isSelected
                        role = Role.Tab
                    },
                onClick = { onTabSelected(tab) },
                border = BorderStroke(TAB_CHIP_BORDER_WIDTH, borderColor),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = resolveTabContainerColor(
                        isSelected = isSelected,
                        primaryColor = MaterialTheme.colorScheme.primary,
                        surfaceColor = MaterialTheme.colorScheme.surface
                    ),
                    labelColor = resolveTabLabelColor(
                        isSelected = isSelected,
                        primaryColor = MaterialTheme.colorScheme.primary,
                        onSurfaceColor = MaterialTheme.colorScheme.onSurface
                    )
                ),
                label = {
                    Text(
                        text = stringResource(tab.labelResId),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    }
}

private val FLOATING_TAB_STRIP_BOTTOM_PADDING = 0.dp
private val SHEET_CONTENT_HORIZONTAL_PADDING = 16.dp
private val SHEET_CONTENT_TOP_PADDING = 8.dp
private val SHEET_CONTENT_BOTTOM_PADDING = 0.dp
private val SHEET_DIVIDER_TOP_PADDING = 4.dp
private val SHEET_DIVIDER_BOTTOM_PADDING = 1.dp
private val SHEET_TAB_STRIP_BOTTOM_PADDING = 0.dp
private val TAB_CHIP_BORDER_WIDTH = 1.dp
private const val SELECTED_TAB_FILL_ALPHA = 0.16f
private const val SELECTED_TAB_BORDER_ALPHA = 0.55f
internal val RAINVIEWER_TAB_ENABLED_BORDER_COLOR = Color(0xFF16A34A)
internal const val MAP_BOTTOM_TAB_STRIP_TAG = "map_bottom_tab_strip"

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

internal fun resolveTabBorderColor(
    tab: MapBottomTab,
    isSelected: Boolean,
    weatherEnabled: Boolean,
    defaultBorderColor: Color,
    selectedBorderColor: Color
): Color {
    return when {
        isSelected -> selectedBorderColor
        tab == MapBottomTab.RAIN -> resolveRainViewerBorderColor(
            weatherEnabled = weatherEnabled,
            defaultBorderColor = defaultBorderColor
        )
        else -> defaultBorderColor
    }
}

internal fun resolveTabContainerColor(
    isSelected: Boolean,
    primaryColor: Color,
    surfaceColor: Color
): Color {
    return if (isSelected) {
        primaryColor.copy(alpha = SELECTED_TAB_FILL_ALPHA)
    } else {
        surfaceColor
    }
}

internal fun resolveTabLabelColor(
    isSelected: Boolean,
    primaryColor: Color,
    onSurfaceColor: Color
): Color {
    return if (isSelected) {
        primaryColor
    } else {
        onSurfaceColor
    }
}
