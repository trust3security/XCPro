package com.example.dfcards

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.VerticalSpeedUnit
import com.example.xcpro.common.units.UnitsPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class FlightDataViewModelUnitsTest {

    @Test
    fun updateUnitsPreferences_reformatsExistingCardValues() {
        val viewModel = FlightDataViewModel()
        val template = FlightTemplate(
            id = "units-test",
            name = "Units Test",
            description = "Single altitude card",
            cardIds = listOf("agl")
        )

        viewModel.applyTemplate(
            template = template,
            containerSize = IntSize(800, 600),
            density = Density(1f)
        )

        val liveData = RealTimeFlightData(
            agl = 100.0,
            isQNHCalibrated = true,
            qnh = 1013.25
        )
        viewModel.updateCardsWithLiveData(liveData)

        val initial = viewModel.getCardState("agl")?.flightData?.primaryValue
        assertEquals("100 m", initial)

        viewModel.updateUnitsPreferences(
            UnitsPreferences(altitude = AltitudeUnit.FEET)
        )

        val updated = viewModel.getCardState("agl")?.flightData?.primaryValue
        assertEquals("328 ft", updated)
    }

    @Test
    fun updateUnitsPreferences_reformatsVerticalSpeedToKnots() {
        val viewModel = FlightDataViewModel()
        val template = FlightTemplate(
            id = "vario-test",
            name = "Vario Test",
            description = "Single vario card",
            cardIds = listOf("vario")
        )

        viewModel.applyTemplate(
            template = template,
            containerSize = IntSize(800, 600),
            density = Density(1f)
        )

        viewModel.updateCardsWithLiveData(
            RealTimeFlightData(
                verticalSpeed = 1.5
            )
        )

        val initial = viewModel.getCardState("vario")?.flightData?.primaryValue
        assertEquals("+1.5 m/s", initial)

        viewModel.updateUnitsPreferences(
            UnitsPreferences(verticalSpeed = VerticalSpeedUnit.KNOTS)
        )

        val updated = viewModel.getCardState("vario")?.flightData?.primaryValue
        assertEquals("+2.9 kt", updated)
    }
}
