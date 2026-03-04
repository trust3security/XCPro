package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
    fun map4_showsMapControlRows() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.MAP4,
            isSheetVisible = true
        )

        composeTestRule.onNodeWithText("Map controls").assertIsDisplayed()
        composeTestRule.onNodeWithText("ADS-B traffic").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hotspots (TH)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Distance circles").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MAP4_QNH_BUTTON_TAG).assertIsDisplayed()
    }

    @Test
    fun floatingStrip_showsRainViewerWithoutLegacyWeatherSheetControls() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.SKYSIGHT,
            isSheetVisible = false,
            weatherEnabled = true
        )

        composeTestRule.onAllNodesWithText("RainViewer").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Show rain overlay").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("More settings").assertCountEquals(0)
    }

    @Test
    fun floatingStrip_marksSelectedTabAsSelectedSemantics() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.SKYSIGHT,
            isSheetVisible = false,
            weatherEnabled = true
        )

        composeTestRule.onNodeWithTag(MapBottomTab.SKYSIGHT.chipTestTag).assertIsSelected()
        composeTestRule.onNodeWithTag(MapBottomTab.RAIN.chipTestTag).assertIsNotSelected()
        composeTestRule.onNodeWithTag(MapBottomTab.OGN.chipTestTag).assertIsNotSelected()
        composeTestRule.onNodeWithTag(MapBottomTab.MAP4.chipTestTag).assertIsNotSelected()
    }

    @Test
    fun floatingStrip_showsMap4Label() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.SKYSIGHT,
            isSheetVisible = false
        )

        composeTestRule.onNodeWithText("Map4").assertIsDisplayed()
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
    fun resolveTabBorderColor_selectedRain_usesSelectedBorderColor() {
        val defaultBorder = Color.Gray
        val selectedBorder = Color.Red

        val resolved = resolveTabBorderColor(
            tab = MapBottomTab.RAIN,
            isSelected = true,
            weatherEnabled = true,
            defaultBorderColor = defaultBorder,
            selectedBorderColor = selectedBorder
        )

        assertEquals(selectedBorder, resolved)
    }

    @Test
    fun resolveTabContainerColor_selected_usesPrimaryAlphaTint() {
        val primary = Color(0xFF112233)
        val surface = Color(0xFFEEEEEE)

        val selected = resolveTabContainerColor(
            isSelected = true,
            primaryColor = primary,
            surfaceColor = surface
        )
        val unselected = resolveTabContainerColor(
            isSelected = false,
            primaryColor = primary,
            surfaceColor = surface
        )

        assertEquals(primary.copy(alpha = 0.16f), selected)
        assertEquals(surface, unselected)
    }

    @Test
    fun strip_isHorizontallyScrollableForCompactLayouts() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.SKYSIGHT,
            isSheetVisible = false
        )

        composeTestRule.onNodeWithTag(MAP_BOTTOM_TAB_STRIP_TAG)
            .assert(hasScrollByAction())
    }

    @Test
    fun compactWidth_stripKeepsAllTabsReachableByTags() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(180.dp)) {
                    MapBottomTabsLayer(
                        selectedTab = MapBottomTab.RAIN,
                        isSheetVisible = false,
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
                        rainTabContent = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(MAP_BOTTOM_TAB_STRIP_TAG).assert(hasScrollByAction())
        composeTestRule.onNodeWithTag(MapBottomTab.RAIN.chipTestTag).assert(existsMatcher())
        composeTestRule.onNodeWithTag(MapBottomTab.SKYSIGHT.chipTestTag).assert(existsMatcher())
        composeTestRule.onNodeWithTag(MapBottomTab.OGN.chipTestTag).assert(existsMatcher())
        composeTestRule.onNodeWithTag(MapBottomTab.MAP4.chipTestTag).assert(existsMatcher())
    }

    @Test
    fun tabChips_exposeTabRoleSemantics() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.RAIN,
            isSheetVisible = false
        )

        composeTestRule.onNodeWithTag(MapBottomTab.RAIN.chipTestTag)
            .assert(hasRole(Role.Tab))
        composeTestRule.onNodeWithTag(MapBottomTab.SKYSIGHT.chipTestTag)
            .assert(hasRole(Role.Tab))
    }

    @Test
    fun floatingStrip_rainViewerClick_selectsRainTab() {
        var selectedTab: MapBottomTab? = null

        setBottomTabsContent(
            selectedTab = MapBottomTab.SKYSIGHT,
            isSheetVisible = false,
            onTabSelected = { selectedTab = it }
        )

        composeTestRule.onNodeWithTag(MapBottomTab.RAIN.chipTestTag).performClick()
        composeTestRule.runOnIdle {
            assertTrue(selectedTab == MapBottomTab.RAIN)
        }
    }

    @Test
    fun rainTab_rendersInjectedContentInsideSharedSheetHost() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.RAIN,
            isSheetVisible = true,
            weatherEnabled = true,
            rainTabContent = { Text("Rain tab content sentinel") }
        )

        composeTestRule.onNodeWithText("Rain tab content sentinel").assertIsDisplayed()
    }

    @Test
    fun sheet_tabSwitch_keepsSharedHostVisible() {
        composeTestRule.setContent {
            MaterialTheme {
                var selectedTab by mutableStateOf(MapBottomTab.RAIN)
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

        composeTestRule.onNodeWithText("Rain tab content sentinel").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapBottomTab.SKYSIGHT.chipTestTag).performClick()
        composeTestRule.onNodeWithTag(MAP_BOTTOM_TAB_STRIP_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("SkySight").assertIsDisplayed()
    }

    private fun setBottomTabsContent(
        selectedTab: MapBottomTab,
        isSheetVisible: Boolean,
        weatherEnabled: Boolean = false,
        onTabSelected: (MapBottomTab) -> Unit = {},
        rainTabContent: @Composable () -> Unit = { Text("Rain content test sentinel") }
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                MapBottomTabsLayer(
                    selectedTab = selectedTab,
                    isSheetVisible = isSheetVisible,
                    isTaskPanelVisible = false,
                    onTabSelected = onTabSelected,
                    onDismissSheet = {},
                    weatherEnabled = weatherEnabled,
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
                    rainTabContent = rainTabContent
                )
            }
        }
    }

    private fun hasRole(expectedRole: Role): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, expectedRole)

    private fun hasScrollByAction(): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)

    private fun existsMatcher(): SemanticsMatcher =
        SemanticsMatcher("node exists") { true }
}
