package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.xcpro.forecast.ForecastOverlayUiState
import com.example.xcpro.map.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun map4_showsMapControlRows_withoutLegacyHelperCopy() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.MAP4,
            isSheetVisible = true
        )

        composeTestRule.onNodeWithText("ADS-B traffic").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hotspots (TH)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Distance circles").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MAP4_QNH_BUTTON_TAG).assert(existsMatcher())
        composeTestRule.onAllNodesWithText("Map controls").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("QNH 1013.3 hPa").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("These controls replace the map FABs for QNH and circles.")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText("These controls replace the traffic FABs for ADS-B and hotspots.")
            .assertCountEquals(0)
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
    fun defaultMapBottomNavigationIcon_usesBrandLogosForRainAndSkySight() {
        val rainIcon = defaultMapBottomNavigationIcon(MapBottomTab.RAIN)
        val skySightIcon = defaultMapBottomNavigationIcon(MapBottomTab.SKYSIGHT)

        assertTrue(rainIcon is MapBottomNavigationIconSpec.BrandLogo)
        assertTrue(skySightIcon is MapBottomNavigationIconSpec.BrandLogo)
        assertEquals(R.drawable.rainviewer, (rainIcon as MapBottomNavigationIconSpec.BrandLogo).resId)
        assertEquals(
            R.drawable.ic_skysight,
            (skySightIcon as MapBottomNavigationIconSpec.BrandLogo).resId
        )
    }

    @Test
    fun defaultMapBottomNavigationIcon_usesFallbackVectorForSciaAndBrandLogoForXcPro() {
        val sciaIcon = defaultMapBottomNavigationIcon(MapBottomTab.OGN)
        val xcProIcon = defaultMapBottomNavigationIcon(MapBottomTab.MAP4)

        assertTrue(sciaIcon is MapBottomNavigationIconSpec.VectorIcon)
        assertTrue(xcProIcon is MapBottomNavigationIconSpec.BrandLogo)
        assertEquals(R.drawable.xcpro_logo, (xcProIcon as MapBottomNavigationIconSpec.BrandLogo).resId)
    }

    @Test
    fun strip_isNotHorizontallyScrollableForCompactLayouts() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.SKYSIGHT,
            isSheetVisible = false
        )

        composeTestRule.onNodeWithTag(MAP_BOTTOM_TAB_STRIP_TAG)
            .assert(hasNoScrollByAction())
    }

    @Test
    fun compactWidth_barKeepsAllTabsReachableByTagsWithoutScrollSemantics() {
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

        composeTestRule.onNodeWithTag(MAP_BOTTOM_TAB_STRIP_TAG).assert(hasNoScrollByAction())
        composeTestRule.onNodeWithTag(MapBottomTab.RAIN.chipTestTag).assert(existsMatcher())
        composeTestRule.onNodeWithTag(MapBottomTab.SKYSIGHT.chipTestTag).assert(existsMatcher())
        composeTestRule.onNodeWithTag(MapBottomTab.OGN.chipTestTag).assert(existsMatcher())
        composeTestRule.onNodeWithTag(MapBottomTab.MAP4.chipTestTag).assert(existsMatcher())
    }

    @Test
    fun fontScale130_barKeepsAllTabsReachableWithoutScrollSemantics() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.SKYSIGHT,
            isSheetVisible = false,
            containerWidth = 360.dp,
            fontScale = 1.3f
        )

        composeTestRule.onNodeWithTag(MAP_BOTTOM_TAB_STRIP_TAG).assert(hasNoScrollByAction())
        composeTestRule.onNodeWithTag(MapBottomTab.RAIN.chipTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapBottomTab.SKYSIGHT.chipTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapBottomTab.OGN.chipTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapBottomTab.MAP4.chipTestTag).assertIsDisplayed()
    }

    @Test
    fun fontScale150_barKeepsAllTabsReachableWithoutScrollSemantics() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.SKYSIGHT,
            isSheetVisible = false,
            containerWidth = 360.dp,
            fontScale = 1.5f
        )

        composeTestRule.onNodeWithTag(MAP_BOTTOM_TAB_STRIP_TAG).assert(hasNoScrollByAction())
        composeTestRule.onNodeWithTag(MapBottomTab.RAIN.chipTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapBottomTab.SKYSIGHT.chipTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapBottomTab.OGN.chipTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapBottomTab.MAP4.chipTestTag).assertIsDisplayed()
    }

    @Test
    fun isMapBottomNavigationRoute_onlyAllowsMapExperienceRoutes() {
        assertTrue(isMapBottomNavigationRoute("map"))
        assertTrue(isMapBottomNavigationRoute("map?from=startup"))
        assertTrue(isMapBottomNavigationRoute("livefollow/friends"))
        assertTrue(isMapBottomNavigationRoute("livefollow/friends?focus=pilot"))
        assertFalse(isMapBottomNavigationRoute("mapp"))
        assertFalse(isMapBottomNavigationRoute("map/details"))
        assertFalse(isMapBottomNavigationRoute("livefollow/friends-extra"))
        assertFalse(isMapBottomNavigationRoute("prefix/livefollow/friends"))
        assertFalse(isMapBottomNavigationRoute("livefollow/pilot"))
        assertFalse(isMapBottomNavigationRoute("livefollow/watch/share"))
        assertFalse(isMapBottomNavigationRoute("about"))
        assertFalse(isMapBottomNavigationRoute("task"))
        assertFalse(isMapBottomNavigationRoute(null))
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
    fun floatingStrip_rainViewerTab_exposesClickAction() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.SKYSIGHT,
            isSheetVisible = false
        )

        composeTestRule
            .onNodeWithTag(MapBottomTab.RAIN.chipTestTag)
            .assert(hasClickAction())
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
    fun sheetFooter_hidesDuplicateTabLabels() {
        setBottomTabsContent(
            selectedTab = MapBottomTab.RAIN,
            isSheetVisible = true,
            weatherEnabled = true
        )

        composeTestRule.onAllNodesWithText("RainViewer").assertCountEquals(0)
    }

    @Test
    fun sheet_tabSwitch_keepsSharedHostVisible() {
        var selectedTab by mutableStateOf(MapBottomTab.RAIN)

        composeTestRule.setContent {
            MaterialTheme {
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
        composeTestRule.runOnIdle {
            selectedTab = MapBottomTab.SKYSIGHT
        }
        composeTestRule.onNodeWithTag(MAP_BOTTOM_TAB_STRIP_TAG).assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(MapBottomTab.SKYSIGHT, selectedTab)
        }
    }

    private fun setBottomTabsContent(
        selectedTab: MapBottomTab,
        isSheetVisible: Boolean,
        weatherEnabled: Boolean = false,
        containerWidth: Dp? = null,
        fontScale: Float = 1f,
        onTabSelected: (MapBottomTab) -> Unit = {},
        rainTabContent: @Composable () -> Unit = { Text("Rain content test sentinel") }
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = baseDensity.density,
                        fontScale = fontScale
                    )
                ) {
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
    }

    private fun hasRole(expectedRole: Role): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, expectedRole)

    private fun hasNoScrollByAction(): SemanticsMatcher =
        SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollBy)

    private fun hasClickAction(): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)

    private fun existsMatcher(): SemanticsMatcher =
        SemanticsMatcher("node exists") { true }
}
