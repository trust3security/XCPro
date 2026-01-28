package com.example.dfcards

import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.VerticalSpeedUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.core.common.geometry.IntSizePx
import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FlightDataViewModelUnitsTest {
    private fun buildViewModel(clock: FakeClock = FakeClock(monoMs = 1_000L)): FlightDataViewModel {
        return FlightDataViewModel(clock = clock)
    }

    @Test
    fun updateUnitsPreferences_reformatsExistingCardValues() {
        val viewModel = buildViewModel()
        val template = FlightTemplate(
            id = "units-test",
            name = "Units Test",
            description = "Single altitude card",
            cardIds = listOf("agl")
        )

        runBlocking {
            viewModel.applyTemplate(
                template = template,
                containerSize = IntSizePx(800, 600),
                density = DensityScale(1f)
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
        val viewModel = buildViewModel()
        val template = FlightTemplate(
            id = "vario-test",
            name = "Vario Test",
            description = "Single vario card",
            cardIds = listOf("vario")
        )

        runBlocking {
            viewModel.applyTemplate(
                template = template,
                containerSize = IntSizePx(800, 600),
                density = DensityScale(1f)
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
        val viewModel = buildViewModel()
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
        val viewModel = buildViewModel()
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
        val viewModel = buildViewModel()
        val profileId = "profile-template"

        viewModel.setProfileTemplate(profileId, FlightModeSelection.THERMAL, "thermal-template")

        assertEquals(
            "thermal-template",
            viewModel.getProfileTemplateId(profileId, FlightModeSelection.THERMAL)
        )
    }

    @Test
    fun clearProfile_resetsMappings() {
        val viewModel = buildViewModel()
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
