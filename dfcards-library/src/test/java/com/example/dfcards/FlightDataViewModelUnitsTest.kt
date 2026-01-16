package com.example.dfcards

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.VerticalSpeedUnit
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.runBlocking
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

        runBlocking {
            viewModel.applyTemplate(
                template = template,
                containerSize = IntSize(800, 600),
                density = Density(1f)
            )
        }

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

        runBlocking {
            viewModel.applyTemplate(
                template = template,
                containerSize = IntSize(800, 600),
                density = Density(1f)
            )
        }

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

    @Test
    fun setProfileCards_updatesActiveSelection() {
        val viewModel = FlightDataViewModel()
        val profileId = "profile-1"

        viewModel.setActiveProfile(profileId)
        viewModel.setFlightMode(FlightModeSelection.THERMAL)
        viewModel.setProfileCards(profileId, FlightModeSelection.THERMAL, listOf("vario", "netto"))

        assertEquals(
            listOf("vario", "netto"),
            viewModel.profileModeCards.value[profileId]?.get(FlightModeSelection.THERMAL)
        )
    }

    @Test
    fun switchingProfiles_updatesActiveCardIds() {
        val viewModel = FlightDataViewModel()
        val profileA = "pilot-a"
        val profileB = "pilot-b"

        viewModel.setProfileCards(profileA, FlightModeSelection.CRUISE, listOf("track"))
        viewModel.setProfileCards(profileB, FlightModeSelection.CRUISE, listOf("ground_speed", "agl"))

        viewModel.setActiveProfile(profileA)
        viewModel.setFlightMode(FlightModeSelection.CRUISE)
        assertEquals(
            listOf("track"),
            viewModel.profileModeCards.value[profileA]?.get(FlightModeSelection.CRUISE)
        )

        viewModel.setActiveProfile(profileB)
        assertEquals(
            listOf("ground_speed", "agl"),
            viewModel.profileModeCards.value[profileB]?.get(FlightModeSelection.CRUISE)
        )
    }

    @Test
    fun setProfileTemplate_persistsMapping() {
        val viewModel = FlightDataViewModel()
        val profileId = "profile-template"

        viewModel.setProfileTemplate(profileId, FlightModeSelection.THERMAL, "thermal-template")

        assertEquals(
            "thermal-template",
            viewModel.getProfileTemplateId(profileId, FlightModeSelection.THERMAL)
        )
    }

    @Test
    fun clearProfile_resetsMappings() {
        val viewModel = FlightDataViewModel()
        val profileId = "clear-me"

        viewModel.setActiveProfile(profileId)
        viewModel.setFlightMode(FlightModeSelection.FINAL_GLIDE)
        viewModel.setProfileCards(profileId, FlightModeSelection.FINAL_GLIDE, listOf("final_glide"))
        viewModel.setProfileTemplate(profileId, FlightModeSelection.FINAL_GLIDE, "id03")

        viewModel.clearProfile(profileId)

        assertEquals(null, viewModel.activeProfileId.value)
        assertEquals(emptyList<String>(), viewModel.activeCardIds.value)
        assertEquals(emptyList<String>(), viewModel.getProfileCards(profileId, FlightModeSelection.FINAL_GLIDE))
        assertEquals(null, viewModel.getProfileTemplateId(profileId, FlightModeSelection.FINAL_GLIDE))
    }
}
