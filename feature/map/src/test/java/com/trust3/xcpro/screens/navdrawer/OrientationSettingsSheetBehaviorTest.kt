package com.trust3.xcpro.screens.navdrawer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrientationSettingsSheetBehaviorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun swipeDown_closesInOneGesture() {
        var showSheet by mutableStateOf(true)
        var dismissCount by mutableIntStateOf(0)

        composeTestRule.setContent {
            MaterialTheme {
                if (showSheet) {
                    OrientationSettingsSheet(
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
                        Text("Orientation settings test body")
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag(ORIENTATION_SETTINGS_SHEET_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ORIENTATION_SETTINGS_SHEET_TAG).performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(ORIENTATION_SETTINGS_SHEET_TAG).assertCountEquals(0)
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
                    OrientationSettingsSheet(
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
                        Text("Orientation settings test body")
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag(ORIENTATION_SETTINGS_SHEET_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(SETTINGS_TOP_APP_BAR_NAV_BACK_TAG).assertCountEquals(1)
        composeTestRule.onAllNodesWithTag(SETTINGS_TOP_APP_BAR_NAV_BACK_TAG)
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertEquals(false, showSheet)
            assertEquals(1, dismissCount)
        }
    }
}
