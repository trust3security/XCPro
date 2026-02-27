package com.example.xcpro.map.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.xcpro.forecast.ForecastOverlayUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapBottomSheetTabsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tab4_showsMapControlRows() {
        composeTestRule.setContent {
            MaterialTheme {
                MapBottomTabsLayer(
                    selectedTab = MapBottomTab.TAB_4,
                    isSheetVisible = true,
                    isTaskPanelVisible = false,
                    onTabSelected = {},
                    onDismissSheet = {},
                    weatherEnabled = false,
                    weatherOpacity = 0.6f,
                    weatherCyclePastWindow = false,
                    onWeatherEnabledChanged = {},
                    onWeatherOpacityChanged = {},
                    onWeatherCyclePastWindowChanged = {},
                    isDrawerBlocked = false,
                    onOpenWeatherSettingsFromTab = {},
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
                    onSkySightSecondaryPrimaryOverlayEnabledChanged = {},
                    onSkySightSecondaryPrimaryParameterSelected = {},
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
                    onSkySightSatViewEnabledChanged = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Map controls").assertIsDisplayed()
        composeTestRule.onNodeWithText("ADS-B traffic").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hotspots (TH)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Distance circles").assertIsDisplayed()
        composeTestRule.onNodeWithTag(TAB4_QNH_BUTTON_TAG).assertIsDisplayed()
    }
}
