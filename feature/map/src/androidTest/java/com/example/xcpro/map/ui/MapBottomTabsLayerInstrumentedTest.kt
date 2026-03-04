package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.xcpro.forecast.ForecastOverlayUiState
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@Ignore("Compose hierarchy is unavailable in current module-level connected test harness; behavior is covered by Robolectric tests.")
@RunWith(AndroidJUnit4::class)
class MapBottomTabsLayerInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rainSheet_keepsMapSentinelVisibleBehindSheet() {
        setSheetContent(selectedTab = MapBottomTab.RAIN)

        composeTestRule.onNodeWithTag(MAP_SENTINEL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("RainViewer").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MAP_BOTTOM_TAB_STRIP_TAG).assertIsDisplayed()
    }

    @Test
    fun sheet_tabSwitch_keepsSharedHostVisible() {
        composeTestRule.setContent {
            MaterialTheme {
                var selectedTab by mutableStateOf(MapBottomTab.RAIN)
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Map sentinel",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag(MAP_SENTINEL_TAG)
                    )
                    MapBottomTabsLayer(
                        selectedTab = selectedTab,
                        isSheetVisible = true,
                        isTaskPanelVisible = false,
                        onTabSelected = { selectedTab = it },
                        onDismissSheet = {},
                        weatherEnabled = true,
                        ognEnabled = false,
                        showSciaEnabled = false,
                        onOgnEnabledChanged = {},
                        onShowSciaEnabledChanged = {},
                        adsbTrafficEnabled = false,
                        showOgnThermalsEnabled = false,
                        showDistanceCircles = false,
                        currentQnhLabel = "1013.3 hPa",
                        onAdsbTrafficEnabledChanged = {},
                        onShowOgnThermalsEnabledChanged = {},
                        onShowDistanceCirclesChanged = {},
                        onOpenQnhDialogFromTab = {},
                        ognTrailAircraftRows = emptyList(),
                        onOgnTrailAircraftToggled = { _, _ -> },
                        skySightUiState = ForecastOverlayUiState(),
                        onSkySightEnabledChanged = {},
                        onSkySightPrimaryParameterToggled = {},
                        onSkySightWindOverlayEnabledChanged = {},
                        onSkySightWindParameterSelected = {},
                        onSkySightAutoTimeEnabledChanged = {},
                        onSkySightFollowTimeOffsetChanged = {},
                        onSkySightJumpToNow = {},
                        onSkySightTimeSelected = {},
                        onSkySightSatelliteOverlayEnabledChanged = {},
                        onSkySightSatelliteImageryEnabledChanged = {},
                        onSkySightSatelliteRadarEnabledChanged = {},
                        onSkySightSatelliteLightningEnabledChanged = {},
                        onSkySightSatelliteAnimateEnabledChanged = {},
                        onSkySightSatelliteHistoryFramesChanged = {},
                        skySightWarningMessage = null,
                        skySightErrorMessage = null,
                        skySightSatViewEnabled = false,
                        onSkySightSatViewEnabledChanged = {},
                        rainTabContent = { Text("Rain tab content sentinel") }
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(MAP_SENTINEL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MAP_BOTTOM_TAB_STRIP_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapBottomTab.SKYSIGHT.chipTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapBottomTab.SKYSIGHT.chipTestTag).performClick()
        composeTestRule.onNodeWithTag(MAP_BOTTOM_TAB_STRIP_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MAP_SENTINEL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("SkySight").assertIsDisplayed()
    }

    private fun setSheetContent(selectedTab: MapBottomTab) {
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Map sentinel",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag(MAP_SENTINEL_TAG)
                    )
                    MapBottomTabsLayer(
                        selectedTab = selectedTab,
                        isSheetVisible = true,
                        isTaskPanelVisible = false,
                        onTabSelected = {},
                        onDismissSheet = {},
                        weatherEnabled = true,
                        ognEnabled = false,
                        showSciaEnabled = false,
                        onOgnEnabledChanged = {},
                        onShowSciaEnabledChanged = {},
                        adsbTrafficEnabled = false,
                        showOgnThermalsEnabled = false,
                        showDistanceCircles = false,
                        currentQnhLabel = "1013.3 hPa",
                        onAdsbTrafficEnabledChanged = {},
                        onShowOgnThermalsEnabledChanged = {},
                        onShowDistanceCirclesChanged = {},
                        onOpenQnhDialogFromTab = {},
                        ognTrailAircraftRows = emptyList(),
                        onOgnTrailAircraftToggled = { _, _ -> },
                        skySightUiState = ForecastOverlayUiState(),
                        onSkySightEnabledChanged = {},
                        onSkySightPrimaryParameterToggled = {},
                        onSkySightWindOverlayEnabledChanged = {},
                        onSkySightWindParameterSelected = {},
                        onSkySightAutoTimeEnabledChanged = {},
                        onSkySightFollowTimeOffsetChanged = {},
                        onSkySightJumpToNow = {},
                        onSkySightTimeSelected = {},
                        onSkySightSatelliteOverlayEnabledChanged = {},
                        onSkySightSatelliteImageryEnabledChanged = {},
                        onSkySightSatelliteRadarEnabledChanged = {},
                        onSkySightSatelliteLightningEnabledChanged = {},
                        onSkySightSatelliteAnimateEnabledChanged = {},
                        onSkySightSatelliteHistoryFramesChanged = {},
                        skySightWarningMessage = null,
                        skySightErrorMessage = null,
                        skySightSatViewEnabled = false,
                        onSkySightSatViewEnabledChanged = {},
                        rainTabContent = { Text("Rain tab content sentinel") }
                    )
                }
            }
        }
    }

    private companion object {
        const val MAP_SENTINEL_TAG = "map_sentinel"
    }
}
