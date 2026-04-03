package com.example.xcpro.map.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.forecast.ForecastOverlayUiState
import com.example.xcpro.forecast.ForecastParameterId
import com.example.xcpro.map.R

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
    onShowSciaEnabledChanged: (Boolean) -> Unit,
    adsbTrafficEnabled: Boolean,
    showOgnThermalsEnabled: Boolean,
    showDistanceCircles: Boolean,
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
        MapWeatherRainTabContent(
            title = stringResource(R.string.map_bottom_tab_rainviewer)
        )
    }
) {
    val map4Enabled = adsbTrafficEnabled || showOgnThermalsEnabled || showDistanceCircles
    val navigationItems = mapBottomNavigationItems(
        weatherEnabled = weatherEnabled,
        skySightEnabled = skySightUiState.enabled,
        map4Enabled = map4Enabled
    )

    if (!isSheetVisible && !isTaskPanelVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(65f)
        ) {
            MapBottomNavigationBar(
                selectedTab = selectedTab,
                items = navigationItems,
                onTabSelected = onTabSelected,
                shape = RoundedCornerShape(topStart = FLOATING_TAB_STRIP_TOP_RADIUS, topEnd = FLOATING_TAB_STRIP_TOP_RADIUS),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )
        }
    }

    if (isSheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        LaunchedEffect(sheetState) {
            if (!sheetState.isVisible) {
                sheetState.show()
            }
        }

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
                            MapForecastSkySightTabContent(
                                title = stringResource(R.string.map_bottom_tab_skysight),
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
                                warningMessage = skySightWarningMessage,
                                errorMessage = skySightErrorMessage
                            )
                        }

                        MapBottomTab.OGN -> {
                            MapTrafficOgnTabContent(
                                ognEnabled = ognEnabled,
                                showSciaEnabled = showSciaEnabled,
                                onShowSciaEnabledChanged = onShowSciaEnabledChanged,
                                aircraftRows = ognTrailAircraftRows,
                                onAircraftTrailToggled = onOgnTrailAircraftToggled
                            )
                        }

                        MapBottomTab.MAP4 -> {
                            MapTrafficMap4ControlsContent(
                                adsbTrafficEnabled = adsbTrafficEnabled,
                                showOgnThermalsEnabled = showOgnThermalsEnabled,
                                onAdsbTrafficEnabledChanged = onAdsbTrafficEnabledChanged,
                                onShowOgnThermalsEnabledChanged = onShowOgnThermalsEnabledChanged
                            )
                            Map4MapControlsContent(
                                showDistanceCircles = showDistanceCircles,
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

                MapBottomNavigationBar(
                    selectedTab = selectedTab,
                    items = navigationItems,
                    onTabSelected = onTabSelected,
                    showLabels = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = SHEET_TAB_STRIP_BOTTOM_PADDING)
                )
            }
        }
    }
}

@Composable
private fun mapBottomNavigationItems(
    weatherEnabled: Boolean,
    skySightEnabled: Boolean,
    map4Enabled: Boolean,
): List<MapBottomNavigationItemSpec> {
    return MapBottomTab.entries.map { tab ->
        MapBottomNavigationItemSpec(
            tab = tab,
            label = stringResource(tab.labelResId),
            testTag = tab.chipTestTag,
            icon = defaultMapBottomNavigationIcon(tab),
            isFeatureEnabled = isTabFeatureEnabled(
                tab = tab,
                weatherEnabled = weatherEnabled,
                skySightEnabled = skySightEnabled,
                map4Enabled = map4Enabled
            )
        )
    }
}

private val SHEET_CONTENT_HORIZONTAL_PADDING = 16.dp
private val SHEET_CONTENT_TOP_PADDING = 8.dp
private val SHEET_CONTENT_BOTTOM_PADDING = 0.dp
private val SHEET_DIVIDER_TOP_PADDING = 4.dp
private val SHEET_DIVIDER_BOTTOM_PADDING = 1.dp
private val SHEET_TAB_STRIP_BOTTOM_PADDING = 4.dp
private val FLOATING_TAB_STRIP_TOP_RADIUS = 28.dp

internal fun isTabFeatureEnabled(
    tab: MapBottomTab,
    weatherEnabled: Boolean,
    skySightEnabled: Boolean,
    map4Enabled: Boolean
): Boolean {
    return when (tab) {
        MapBottomTab.RAIN -> weatherEnabled
        MapBottomTab.SKYSIGHT -> skySightEnabled
        MapBottomTab.MAP4 -> map4Enabled
        MapBottomTab.OGN -> false
    }
}
