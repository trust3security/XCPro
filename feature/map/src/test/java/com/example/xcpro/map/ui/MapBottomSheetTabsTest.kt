package com.example.xcpro.map.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.xcpro.forecast.ForecastOverlayUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                    onRainViewerSelected = {},
                    weatherEnabled = false,
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

    @Test
    fun floatingStrip_showsRainViewerWithoutLegacyWeatherSheetControls() {
        composeTestRule.setContent {
            MaterialTheme {
                MapBottomTabsLayer(
                    selectedTab = MapBottomTab.SKYSIGHT,
                    isSheetVisible = false,
                    isTaskPanelVisible = false,
                    onTabSelected = {},
                    onDismissSheet = {},
                    onRainViewerSelected = {},
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
                    onSkySightSatViewEnabledChanged = {}
                )
            }
        }

        composeTestRule.onAllNodesWithText("RainViewer").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Show rain overlay").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("More settings").assertCountEquals(0)
    }

    @Test
    fun resolveRainViewerBorderColor_weatherEnabled_usesGreenBorder() {
        val defaultBorder = Color.Gray

        assertEquals(
            RAINVIEWER_TAB_ENABLED_BORDER_COLOR,
            resolveRainViewerBorderColor(
                weatherEnabled = true,
                defaultBorderColor = defaultBorder
            )
        )
        assertEquals(
            defaultBorder,
            resolveRainViewerBorderColor(
                weatherEnabled = false,
                defaultBorderColor = defaultBorder
            )
        )
    }

    @Test
    fun floatingStrip_rainViewerClick_invokesSelectionCallback() {
        var clicked = false

        composeTestRule.setContent {
            MaterialTheme {
                MapBottomTabsLayer(
                    selectedTab = MapBottomTab.SKYSIGHT,
                    isSheetVisible = false,
                    isTaskPanelVisible = false,
                    onTabSelected = {},
                    onDismissSheet = {},
                    onRainViewerSelected = { clicked = true },
                    weatherEnabled = false,
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
                    onSkySightSatViewEnabledChanged = {}
                )
            }
        }

        composeTestRule.onNodeWithText("RainViewer").performClick()
        composeTestRule.runOnIdle {
            assertTrue(clicked)
        }
    }
}
