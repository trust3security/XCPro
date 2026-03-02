package com.example.xcpro.screens.navdrawer

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherSettingsSheetBehaviorInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun swipeDown_closesInOneGesture() {
        var showSheet by mutableStateOf(true)
        var dismissCount by mutableIntStateOf(0)

        composeTestRule.setContent {
            MaterialTheme {
                if (showSheet) {
                    WeatherSettingsSheet(
                        onDismissRequest = {
                            dismissCount += 1
                            showSheet = false
                        },
                        onNavigateUp = {
                            dismissCount += 1
                            showSheet = false
                        },
                        onSecondaryNavigate = null,
                        onNavigateToMap = {}
                    ) {
                        Text("Rain settings test body")
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag(WEATHER_SETTINGS_SHEET_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(WEATHER_SETTINGS_SHEET_TAG).performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(WEATHER_SETTINGS_SHEET_TAG).assertCountEquals(0)
        composeTestRule.runOnIdle {
            assertEquals(1, dismissCount)
        }
    }

    @Test
    fun backAction_closesSheet() {
        var showSheet by mutableStateOf(true)
        var dismissCount by mutableIntStateOf(0)

        composeTestRule.setContent {
            MaterialTheme {
                if (showSheet) {
                    WeatherSettingsSheet(
                        onDismissRequest = {
                            dismissCount += 1
                            showSheet = false
                        },
                        onNavigateUp = {
                            dismissCount += 1
                            showSheet = false
                        },
                        onSecondaryNavigate = null,
                        onNavigateToMap = {}
                    ) {
                        Text("Rain settings test body")
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag(WEATHER_SETTINGS_SHEET_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(WEATHER_SETTINGS_SHEET_TAG).assertCountEquals(0)
        composeTestRule.runOnIdle {
            assertEquals(1, dismissCount)
        }
    }
}
