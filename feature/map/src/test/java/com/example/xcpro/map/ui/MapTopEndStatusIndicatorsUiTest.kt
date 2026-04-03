package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.example.xcpro.livefollow.pilot.LiveFollowPilotMapStatusHost
import com.example.xcpro.livefollow.pilot.LiveFollowPilotShareIndicatorState
import com.example.xcpro.livefollow.pilot.LiveFollowPilotUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapTopEndStatusIndicatorsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun liveFollowIndicator_alignsUnderTrafficIndicatorStack() {
        val indicators = TrafficConnectionIndicatorsUiState(
            ogn = TrafficConnectionIndicatorUiModel(
                sourceLabel = "OGN",
                tone = TrafficConnectionIndicatorTone.GREEN,
                presentation = TrafficConnectionIndicatorPresentation.Dot
            ),
            adsb = TrafficConnectionIndicatorUiModel(
                sourceLabel = "ADS-B",
                tone = TrafficConnectionIndicatorTone.RED,
                presentation = TrafficConnectionIndicatorPresentation.Dot
            )
        )
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 240.dp, height = 240.dp)) {
                    MapTrafficConnectionIndicatorsHost(
                        indicators = indicators,
                        reserveTopEndPrimarySlot = false
                    )
                    LiveFollowPilotMapStatusHost(
                        visible = true,
                        topEndAdditionalOffset = indicators.followingIndicatorTopOffset(),
                        uiState = LiveFollowPilotUiState(
                            shareIndicatorState = LiveFollowPilotShareIndicatorState.LIVE,
                            canStopSharing = true
                        ),
                        onStartSharing = {},
                        onStopSharing = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("OGN connected").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("ADS-B failed").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("LiveFollow sharing live").assertIsDisplayed()

        val (ognBounds, adsbBounds, liveFollowBounds) = composeTestRule.runOnIdle {
            Triple(
                composeTestRule.onNodeWithContentDescription("OGN connected")
                    .fetchSemanticsNode().boundsInRoot,
                composeTestRule.onNodeWithContentDescription("ADS-B failed")
                    .fetchSemanticsNode().boundsInRoot,
                composeTestRule.onNodeWithContentDescription("LiveFollow sharing live")
                    .fetchSemanticsNode().boundsInRoot
            )
        }

        assertRightAligned(ognBounds, adsbBounds)
        assertRightAligned(adsbBounds, liveFollowBounds)
        assertEquals(
            adsbBounds.top - ognBounds.top,
            liveFollowBounds.top - adsbBounds.top,
            1f
        )
    }

    private fun assertRightAligned(first: Rect, second: Rect) {
        assertEquals(first.right, second.right, 1f)
    }
}
