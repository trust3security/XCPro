package com.trust3.xcpro.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapTrafficConnectionIndicatorsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun host_rendersIconOnlyIndicators_preservingAccessibilityDescriptions() {
        composeTestRule.setContent {
            Box {
                MapTrafficConnectionIndicatorsHost(
                    indicators = TrafficConnectionIndicatorsUiState(
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
                    ),
                    reserveTopEndPrimarySlot = false
                )
            }
        }

        composeTestRule.onAllNodesWithText("OGN").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("ADS-B").assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription("OGN connected").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("ADS-B failed").assertIsDisplayed()
    }

    @Test
    fun host_rendersLostCardsWithVisibleCopy() {
        composeTestRule.setContent {
            Box {
                MapTrafficConnectionIndicatorsHost(
                    indicators = TrafficConnectionIndicatorsUiState(
                        ogn = TrafficConnectionIndicatorUiModel(
                            sourceLabel = "OGN",
                            tone = TrafficConnectionIndicatorTone.RED,
                            presentation = TrafficConnectionIndicatorPresentation.LostCard(
                                message = "OGN connection lost"
                            )
                        ),
                        adsb = TrafficConnectionIndicatorUiModel(
                            sourceLabel = "ADS-B",
                            tone = TrafficConnectionIndicatorTone.RED,
                            presentation = TrafficConnectionIndicatorPresentation.LostCard(
                                message = "ADS-B signal lost"
                            )
                        )
                    ),
                    reserveTopEndPrimarySlot = false
                )
            }
        }

        composeTestRule.onNodeWithText("OGN connection lost").assertIsDisplayed()
        composeTestRule.onNodeWithText("ADS-B signal lost").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("OGN connection lost").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("ADS-B signal lost").assertIsDisplayed()
    }
}
