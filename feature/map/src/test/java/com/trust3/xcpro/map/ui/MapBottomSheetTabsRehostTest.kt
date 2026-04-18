package com.trust3.xcpro.map.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.trust3.xcpro.forecast.ForecastOverlayUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapBottomSheetTabsRehostTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sheet_visibilityFlagTurningTrue_presentsVisibleContent() {
        var isSheetVisible by mutableStateOf(false)

        composeTestRule.setContent {
            MaterialTheme {
                MapBottomTabsLayer(
                    selectedTab = MapBottomTab.RAIN,
                    isSheetVisible = isSheetVisible,
                    isTaskPanelVisible = false,
                    onTabSelected = {},
                    onDismissSheet = {},
                    weatherEnabled = true,
                    ognEnabled = false,
                    showSciaEnabled = false,
                    onShowSciaEnabledChanged = {},
                    adsbTrafficEnabled = false,
                    showOgnThermalsEnabled = false,
                    showDistanceCircles = false,
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

        composeTestRule.onAllNodesWithText("Rain tab content sentinel").assertCountEquals(0)
        composeTestRule.runOnIdle { isSheetVisible = true }
        composeTestRule.onNodeWithText("Rain tab content sentinel").assertIsDisplayed()
    }

    @Test
    fun sheet_rehostAfterTemporaryRemoval_restoresVisibleContent() {
        var showHost by mutableStateOf(true)

        composeTestRule.setContent {
            MaterialTheme {
                if (showHost) {
                    MapBottomTabsLayer(
                        selectedTab = MapBottomTab.RAIN,
                        isSheetVisible = true,
                        isTaskPanelVisible = false,
                        onTabSelected = {},
                        onDismissSheet = {},
                        weatherEnabled = true,
                        ognEnabled = false,
                        showSciaEnabled = false,
                        onShowSciaEnabledChanged = {},
                        adsbTrafficEnabled = false,
                        showOgnThermalsEnabled = false,
                        showDistanceCircles = false,
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

        composeTestRule.onNodeWithText("Rain tab content sentinel").assertIsDisplayed()
        composeTestRule.runOnIdle { showHost = false }
        composeTestRule.runOnIdle { showHost = true }
        composeTestRule.onNodeWithText("Rain tab content sentinel").assertIsDisplayed()
    }
}
