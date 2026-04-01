package com.example.xcpro.map.ui

import com.example.dfcards.FlightModeSelection
import com.example.xcpro.MapOrientationManager
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MapScreenOrientationRuntimeEffectsTest {

    @Test
    fun applyOrientationFlightModeSelection_forwardsSelectionToOrientationManager() {
        val orientationManager = mock<MapOrientationManager>()

        applyOrientationFlightModeSelection(
            orientationManager = orientationManager,
            currentFlightModeSelection = FlightModeSelection.CRUISE
        )

        verify(orientationManager).setFlightMode(FlightModeSelection.CRUISE)
    }
}
