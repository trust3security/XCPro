package com.example.xcpro.map.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.xcpro.map.MapLocationRuntimePort
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapScreenLocationProfileBindingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun initialComposition_forwardsCurrentProfileIdOnce() {
        val locationManager = mock<MapLocationRuntimePort>()

        composeTestRule.setContent {
            MapScreenLocationProfileBinding(
                activeProfileId = "pilot-a",
                locationManager = locationManager
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            verify(locationManager, times(1)).setActiveProfileId("pilot-a")
        }
    }

    @Test
    fun profileIdChange_forwardsUpdatedProfileIdOnce() {
        val locationManager = mock<MapLocationRuntimePort>()
        var activeProfileId by mutableStateOf("pilot-a")

        composeTestRule.setContent {
            MapScreenLocationProfileBinding(
                activeProfileId = activeProfileId,
                locationManager = locationManager
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            activeProfileId = "pilot-b"
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            verify(locationManager, times(1)).setActiveProfileId("pilot-a")
            verify(locationManager, times(1)).setActiveProfileId("pilot-b")
        }
    }

    @Test
    fun unrelatedRecomposition_doesNotForwardDuplicateProfileId() {
        val locationManager = mock<MapLocationRuntimePort>()
        var recomposeTrigger by mutableStateOf(0)

        composeTestRule.setContent {
            val ignored = recomposeTrigger
            MapScreenLocationProfileBinding(
                activeProfileId = "pilot-a",
                locationManager = locationManager
            )
            ignored
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            recomposeTrigger++
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            verify(locationManager, times(1)).setActiveProfileId("pilot-a")
        }
    }
}
