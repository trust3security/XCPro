package com.example.xcpro.map.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.xcpro.weather.rain.WeatherOverlayRuntimeState
import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import com.example.xcpro.weather.rain.WeatherRadarRenderOptions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeatherMapConfidenceChipTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun hiddenWhenOverlayDisabled() {
        composeTestRule.setContent {
            MaterialTheme {
                WeatherMapConfidenceChip(
                    runtimeState = WeatherOverlayRuntimeState(enabled = false)
                )
            }
        }

        composeTestRule
            .onAllNodesWithTag(WEATHER_MAP_CONFIDENCE_CHIP_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun showsLiveLabelWhenLive() {
        composeTestRule.setContent {
            MaterialTheme {
                WeatherMapConfidenceChip(
                    runtimeState = WeatherOverlayRuntimeState(
                        enabled = true,
                        selectedFrame = sampleFrame(),
                        metadataStatus = WeatherRadarStatusCode.OK,
                        metadataStale = false
                    )
                )
            }
        }

        composeTestRule.onNodeWithText("Rain Live").assertIsDisplayed()
    }

    @Test
    fun showsStaleLabelWhenStale() {
        composeTestRule.setContent {
            MaterialTheme {
                WeatherMapConfidenceChip(
                    runtimeState = WeatherOverlayRuntimeState(
                        enabled = true,
                        selectedFrame = sampleFrame(),
                        metadataStatus = WeatherRadarStatusCode.OK,
                        metadataStale = true
                    )
                )
            }
        }

        composeTestRule.onNodeWithText("Rain Stale").assertIsDisplayed()
    }

    @Test
    fun showsErrorLabelWhenNoFrame() {
        composeTestRule.setContent {
            MaterialTheme {
                WeatherMapConfidenceChip(
                    runtimeState = WeatherOverlayRuntimeState(
                        enabled = true,
                        selectedFrame = null,
                        metadataStatus = WeatherRadarStatusCode.NO_METADATA,
                        metadataStale = true
                    )
                )
            }
        }

        composeTestRule.onNodeWithText("Rain Error").assertIsDisplayed()
    }

    private fun sampleFrame(): WeatherRainFrameSelection =
        WeatherRainFrameSelection(
            hostUrl = "https://tile.example.test",
            framePath = "/v2/radar/123/256/{z}/{x}/{y}/2/1_1.png",
            frameTimeEpochSec = 1_700_000_000L,
            renderOptions = WeatherRadarRenderOptions()
        )
}
