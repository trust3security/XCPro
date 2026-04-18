package com.trust3.xcpro.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.trust3.xcpro.common.waypoint.WaypointData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommonTaskComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun advanceControls_hideAdvanceLabel_andKeepModeAndArmChipsOnOneLine() {
        composeTestRule.setContent {
            MaterialTheme {
                val snapshot = remember {
                    mutableStateOf(
                        TaskAdvanceUiSnapshot(
                            mode = TaskAdvanceUiSnapshot.Mode.MANUAL,
                            armState = TaskAdvanceUiSnapshot.ArmState.START_DISARMED,
                            isArmed = false
                        )
                    )
                }
                AdvanceControls(
                    snapshot = snapshot.value,
                    onModeChange = {},
                    onToggleArm = {}
                )
            }
        }

        composeTestRule.onAllNodesWithText("Advance").assertCountEquals(0)
        composeTestRule.onNodeWithText("Manual").assertIsDisplayed()
        composeTestRule.onNodeWithText("Auto").assertIsDisplayed()
        composeTestRule.onNodeWithText("Disarmed").assertIsDisplayed()

        val chipTops = composeTestRule.runOnIdle {
            listOf(
                composeTestRule.onNodeWithTag(ADVANCE_MANUAL_CHIP_TAG)
                    .fetchSemanticsNode().boundsInRoot.top,
                composeTestRule.onNodeWithTag(ADVANCE_AUTO_CHIP_TAG)
                    .fetchSemanticsNode().boundsInRoot.top,
                composeTestRule.onNodeWithTag(ADVANCE_ARM_CHIP_TAG)
                    .fetchSemanticsNode().boundsInRoot.top
            )
        }

        val expectedTop = chipTops.first()
        chipTops.forEach { top ->
            assertEquals(expectedTop, top, 1f)
        }
    }

    @Test
    fun persistentWaypointSearchBar_hidesCoordinatesInDropdownResults() {
        composeTestRule.setContent {
            MaterialTheme {
                PersistentWaypointSearchBar(
                    allWaypoints = listOf(
                        WaypointData(
                            name = "Alpha",
                            code = "ALP",
                            country = "AU",
                            latitude = 12.34,
                            longitude = 56.78,
                            elevation = "",
                            style = 1,
                            runwayDirection = null,
                            runwayLength = null,
                            frequency = null,
                            description = null
                        )
                    ),
                    onWaypointSelected = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(PERSISTENT_WAYPOINT_SEARCH_FIELD_TAG).performTextInput("Al")

        composeTestRule.onNodeWithText("Alpha (ALP)").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("12.3400, 56.7800").assertCountEquals(0)
    }
}
