package com.example.xcpro.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.screens.overlays.getMapStyleUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapScreenStateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun updateFlightMode_updatesFlowsAndManager() {
        val mapState = MapScreenState(context, initialMapStyle = "classic")
        val manager: FlightDataManager = mock()
        whenever(manager.currentFlightMode).thenReturn(FlightModeSelection.CRUISE)
        mapState.flightDataManager = manager

        mapState.updateFlightMode(FlightMode.THERMAL)

        assertEquals(FlightMode.THERMAL, mapState.currentModeFlow.value)
        assertEquals(
            FlightModeSelection.THERMAL,
            mapState.currentFlightModeFlow.value
        )
        verify(manager).updateFlightModeFromEnum(FlightMode.THERMAL)
    }

    @Test
    fun showRecenterButtonFlow_reflectsMutationsAndReset() {
        val mapState = MapScreenState(context, initialMapStyle = "classic")

        assertFalse(mapState.showRecenterButtonFlow.value)

        mapState.showRecenterButton = true

        assertTrue(mapState.showRecenterButtonFlow.value)

        mapState.resetToDefaults()

        assertFalse(mapState.showRecenterButtonFlow.value)
    }

    @Test
    fun updateMapStyle_refreshesMapStyleUrlFlow() {
        val mapState = MapScreenState(context, initialMapStyle = "classic")

        mapState.updateMapStyle("terrain")

        assertEquals(
            getMapStyleUrl("terrain"),
            mapState.mapStyleUrlFlow.value
        )
    }
}
