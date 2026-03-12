package com.example.xcpro.screens.navdrawer

import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import com.example.ui1.screens.SettingsScreen
import com.example.ui1.screens.closeGeneralToDrawer
import com.example.ui1.screens.closeGeneralToMap
import com.example.xcpro.navigation.SettingsRoutes
import com.example.xcpro.navigation.TrafficSettingsRoutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeneralSettingsScreenPolicyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun hotspotsRouteConstant_matchesContract() {
        assertEquals("hotspots_settings", TrafficSettingsRoutes.HOTSPOTS_SETTINGS)
    }

    @Test
    fun adsbRouteConstant_matchesContract() {
        assertEquals("adsb_settings", TrafficSettingsRoutes.ADSB_SETTINGS)
    }

    @Test
    fun orientationRouteConstant_matchesContract() {
        assertEquals("orientation_settings", SettingsRoutes.ORIENTATION_SETTINGS)
    }

    @Test
    fun settingsScreen_hidesDiagnosticsAndKeepsHawkOrientationPairing() {
        val navController: NavHostController = mock()
        val drawerState: DrawerState = mock()

        composeTestRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    navController = navController,
                    drawerState = drawerState,
                    onShowAirspaceOverlay = {}
                )
            }
        }

        composeTestRule.onNodeWithText("HAWK Vario").assertIsDisplayed()
        composeTestRule.onNodeWithText("Orientation").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Vario Diagnostics").assertCountEquals(0)
    }

    @Test
    fun settingsScreen_doesNotExposeLegacyAdsbTrafficLabel() {
        val navController: NavHostController = mock()
        val drawerState: DrawerState = mock()

        composeTestRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    navController = navController,
                    drawerState = drawerState,
                    onShowAirspaceOverlay = {}
                )
            }
        }

        composeTestRule.onAllNodesWithText("ADS-b Traffic").assertCountEquals(0)
    }

    @Test
    fun settingsScreen_orientationTile_opensLocalOrientationSubSheet() {
        val navController: NavHostController = mock()
        val drawerState: DrawerState = mock()

        composeTestRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    navController = navController,
                    drawerState = drawerState,
                    onShowAirspaceOverlay = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Orientation").performClick()
        verify(navController, never()).navigate(SettingsRoutes.ORIENTATION_SETTINGS)
    }

    @Test
    fun settingsScreen_filesTile_opensLocalSubSheet() {
        assertTileDoesNotNavigateToRoute("Files", SettingsRoutes.FILES)
    }

    @Test
    fun settingsScreen_profilesTile_opensLocalSubSheet() {
        assertTileDoesNotNavigateToRoute("Profiles", SettingsRoutes.PROFILES)
    }

    @Test
    fun settingsScreen_lookAndFeelTile_opensLocalSubSheet() {
        assertTileDoesNotNavigateToRoute("Look & Feel", "look_and_feel")
    }

    @Test
    fun settingsScreen_unitsTile_opensLocalSubSheet() {
        assertTileDoesNotNavigateToRoute("Units", "units_settings")
    }

    @Test
    fun settingsScreen_polarTile_opensLocalSubSheet() {
        assertTileDoesNotNavigateToRoute("Polar", "polar_settings")
    }

    @Test
    fun settingsScreen_levoVarioTile_opensLocalSubSheet() {
        assertTileDoesNotNavigateToRoute("Levo Vario", "levo_vario_settings")
    }

    @Test
    fun settingsScreen_hawkVarioTile_opensLocalSubSheet() {
        assertTileDoesNotNavigateToRoute("HAWK Vario", "hawk_vario_settings")
    }

    @Test
    fun settingsScreen_layoutsTile_opensLocalSubSheet() {
        assertTileDoesNotNavigateToRoute("Layouts", "layouts")
    }

    @Test
    fun settingsScreen_skysightTile_opensLocalSubSheet() {
        assertTileDoesNotNavigateToRoute("SkySight", "forecast_settings")
    }

    @Test
    fun closeGeneralToMap_whenMapRouteExists_doesNotNavigateUp() = runTest {
        val navController: NavHostController = mock()
        val drawerState: DrawerState = mock()
        whenever(navController.popBackStack("map", false)).thenReturn(true)

        closeGeneralToMap(
            navController = navController,
            drawerState = drawerState
        )

        verify(navController).popBackStack("map", false)
        verify(navController, never()).navigateUp()
    }

    @Test
    fun closeGeneralToMap_whenMapRouteMissing_fallsBackToNavigateUp() = runTest {
        val navController: NavHostController = mock()
        val drawerState: DrawerState = mock()
        whenever(navController.popBackStack("map", false)).thenReturn(false)
        whenever(navController.navigateUp()).thenReturn(true)

        closeGeneralToMap(
            navController = navController,
            drawerState = drawerState
        )

        verify(navController).popBackStack("map", false)
        verify(navController).navigateUp()
    }

    @Test
    fun closeGeneralToDrawer_whenMapRouteExists_opensDrawerWithoutNavigateUp() = runTest {
        val navController: NavHostController = mock()
        val drawerState: DrawerState = mock()
        whenever(navController.popBackStack("map", false)).thenReturn(true)

        closeGeneralToDrawer(
            navController = navController,
            drawerState = drawerState
        )

        verify(navController).popBackStack("map", false)
        verify(navController, never()).navigateUp()
        verify(drawerState).open()
    }

    @Test
    fun closeGeneralToDrawer_whenMapRouteMissing_fallsBackToNavigateUpAndOpensDrawer() = runTest {
        val navController: NavHostController = mock()
        val drawerState: DrawerState = mock()
        whenever(navController.popBackStack("map", false)).thenReturn(false)
        whenever(navController.navigateUp()).thenReturn(true)

        closeGeneralToDrawer(
            navController = navController,
            drawerState = drawerState
        )

        verify(navController).popBackStack("map", false)
        verify(navController).navigateUp()
        verify(drawerState).open()
    }

    @Test
    fun settingsRoutes_doesNotExposeLegacyThermalsRouteConstant() {
        val routeValues = SettingsRoutes::class.java.declaredFields
            .filter { it.type == String::class.java }
            .mapNotNull { field ->
                field.isAccessible = true
                field.get(null) as? String
            }

        assertFalse(routeValues.contains("thermals_settings"))
    }

    private fun assertTileDoesNotNavigateToRoute(tileLabel: String, route: String) {
        val navController: NavHostController = mock()
        val drawerState: DrawerState = mock()

        composeTestRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    navController = navController,
                    drawerState = drawerState,
                    onShowAirspaceOverlay = {}
                )
            }
        }

        composeTestRule.onNodeWithText(tileLabel).performClick()
        verify(navController, never()).navigate(route)
    }
}
