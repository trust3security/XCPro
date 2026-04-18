package com.trust3.xcpro.map.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.dfcards.FlightModeSelection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapScreenOrientationRuntimeEffectsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun initialComposition_forwardsCurrentSelectionToCallback() {
        val forwardedSelections = mutableListOf<FlightModeSelection>()

        composeTestRule.setContent {
            MapScreenOrientationRuntimeEffects(
                currentFlightModeSelection = FlightModeSelection.CRUISE,
                onApplyOrientationFlightModeSelection = forwardedSelections::add
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertEquals(listOf(FlightModeSelection.CRUISE), forwardedSelections)
        }
    }

    @Test
    fun selectionChange_forwardsUpdatedSelectionToCallback() {
        val forwardedSelections = mutableListOf<FlightModeSelection>()
        var currentSelection by mutableStateOf(FlightModeSelection.CRUISE)

        composeTestRule.setContent {
            MapScreenOrientationRuntimeEffects(
                currentFlightModeSelection = currentSelection,
                onApplyOrientationFlightModeSelection = forwardedSelections::add
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            currentSelection = FlightModeSelection.THERMAL
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertEquals(
                listOf(FlightModeSelection.CRUISE, FlightModeSelection.THERMAL),
                forwardedSelections
            )
        }
    }
}
