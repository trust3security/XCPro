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
import androidx.compose.ui.unit.Dp
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
    fun ognTab_ognToggleMovedToGeneralSettings() {
        composeTestRule.setContent {
            MaterialTheme {
                MapBottomTabsLayer(
                    selectedTab = MapBottomTab.OGN,
                    isSheetVisible = true,
                    isTaskPanelVisible = false,
                    onTabSelected = {},
                    onDismissSheet = {},
                    weatherEnabled = false,
                    ognEnabled = true,
                    showSciaEnabled = false,
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

        composeTestRule.onAllNodesWithText("OGN Traffic").assertCountEquals(0)
        composeTestRule.onNodeWithText("Show Scia").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Enable Show Scia to display OGN trails/wake.")
            .assertIsDisplayed()
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
    fun floatingStrip_showsXcProLabel() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.SKYSIGHT,
            isSheetVisible = false
        )

        composeTestRule.onNodeWithText("XCPro").assertIsDisplayed()
    }

    @Test
    fun floatingStrip_wideLayout_displaysAllTabsWithoutClipping() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.SKYSIGHT,
            isSheetVisible = false,
            containerWidth = 420.dp
        )

        composeTestRule.onNodeWithTag(MapBottomTab.RAIN.chipTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapBottomTab.SKYSIGHT.chipTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapBottomTab.OGN.chipTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapBottomTab.MAP4.chipTestTag).assertIsDisplayed()
    }

    @Test
    fun isTabFeatureEnabled_reportsExpectedEnabledStatePerTab() {
        assertTrue(
            isTabFeatureEnabled(
                tab = MapBottomTab.RAIN,
                weatherEnabled = true,
                skySightEnabled = false,
                map4Enabled = false
            )
        )
        assertTrue(
            isTabFeatureEnabled(
                tab = MapBottomTab.SKYSIGHT,
                weatherEnabled = false,
                skySightEnabled = true,
                map4Enabled = false
            )
        )
        assertTrue(
            isTabFeatureEnabled(
                tab = MapBottomTab.MAP4,
                weatherEnabled = false,
                skySightEnabled = false,
                map4Enabled = true
            )
        )
    }

    @Test
    fun resolveTabBorderColor_featureEnabled_usesEnabledBorderColor() {
        val defaultBorder = Color.Gray
        val selectedBorder = Color.Red
        val enabledBorder = Color.Green

        val resolved = resolveTabBorderColor(
            isSelected = true,
            isFeatureEnabled = true,
            defaultBorderColor = defaultBorder,
            selectedBorderColor = selectedBorder,
            enabledBorderColor = enabledBorder
        )

        assertEquals(enabledBorder, resolved)
    }

    @Test
    fun resolveTabContainerColor_usesPrimaryAlphaTintForSelectedAndUnselected() {
        val primary = Color(0xFF112233)

        val selected = resolveTabContainerColor(
            isSelected = true,
            primaryColor = primary
        )
        val unselected = resolveTabContainerColor(
            isSelected = false,
            primaryColor = primary
        )

        assertEquals(primary.copy(alpha = 0.28f), selected)
        assertEquals(primary.copy(alpha = 0.14f), unselected)
    }

    @Test
    fun resolveTabLabelColor_satelliteView_usesWhiteText() {
        val resolved = resolveTabLabelColor(
            isSelected = false,
            satelliteViewEnabled = true,
            primaryColor = Color(0xFF112233),
            onSurfaceColor = Color(0xFFEEEEEE)
        )

        assertEquals(Color.White, resolved)
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
        containerWidth: Dp? = null,
        onTabSelected: (MapBottomTab) -> Unit = {},
        rainTabContent: @Composable () -> Unit = { Text("Rain content test sentinel") }
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                val content: @Composable () -> Unit = {
                    MapBottomTabsLayer(
                        selectedTab = selectedTab,
                        isSheetVisible = isSheetVisible,
                        isTaskPanelVisible = false,
                        onTabSelected = onTabSelected,
                        onDismissSheet = {},
                        weatherEnabled = weatherEnabled,
                        ognEnabled = false,
                        showSciaEnabled = false,
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
                if (containerWidth != null) {
                    Box(modifier = Modifier.width(containerWidth)) {
                        content()
                    }
                } else {
                    content()
                }
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
