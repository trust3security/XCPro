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
import com.example.xcpro.navigation.SettingsRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeneralSettingsScreenPolicyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun hotspotsRouteConstant_matchesContract() {
        assertEquals("hotspots_settings", SettingsRoutes.HOTSPOTS_SETTINGS)
    }

    @Test
    fun settingsScreen_hotspotsButton_navigatesToHotspotsRoute() {
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

        composeTestRule.onNodeWithText("Hotspots").performClick()

        verify(navController).navigate(SettingsRoutes.HOTSPOTS_SETTINGS)
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
    fun settingsRoutes_doesNotExposeLegacyThermalsRouteConstant() {
        val routeValues = SettingsRoutes::class.java.declaredFields
            .filter { it.type == String::class.java }
            .mapNotNull { field ->
                field.isAccessible = true
                field.get(null) as? String
            }

        assertFalse(routeValues.contains("thermals_settings"))
    }
}
