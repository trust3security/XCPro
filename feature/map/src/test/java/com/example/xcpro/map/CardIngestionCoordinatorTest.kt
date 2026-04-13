package com.example.xcpro.map

import com.example.dfcards.FlightModeSelection
import com.example.xcpro.core.flight.RealTimeFlightData
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.units.PressureUnit
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CardIngestionCoordinatorTest {

    @Test
    fun bindCards_isIdempotent() = runTest {
        val cardHydrationReady = MutableStateFlow(true)
        val cardFlightDataFlow = MutableStateFlow<RealTimeFlightData?>(null)
        val unitsPreferencesFlow = MutableStateFlow(UnitsPreferences())
        val viewModel = mockFlightViewModel()
        val coordinator = CardIngestionCoordinator(
            scope = this,
            cardHydrationReady = cardHydrationReady,
            cardFlightDataFlow = cardFlightDataFlow,
            consumeBufferedCardSample = { null },
            unitsPreferencesFlow = unitsPreferencesFlow,
            initializeCardPreferences = { },
            startIndependentClock = { }
        )

        try {
            coordinator.bindCards(viewModel)
            coordinator.bindCards(viewModel)

            val sample = RealTimeFlightData(latitude = 1.0)
            cardFlightDataFlow.value = sample

            advanceUntilIdle()

            verify(viewModel, times(1)).updateCardsWithLiveData(sample)
        } finally {
            coordinator.stop()
        }
    }

    @Test
    fun consumesBufferedSampleWhenHydrationBecomesReady() = runTest {
        val cardHydrationReady = MutableStateFlow(false)
        val cardFlightDataFlow = MutableStateFlow<RealTimeFlightData?>(null)
        val unitsPreferencesFlow = MutableStateFlow(UnitsPreferences())
        val viewModel = mockFlightViewModel()
        val expected = RealTimeFlightData(latitude = 2.0)
        var bufferedSample: RealTimeFlightData? = expected
        val coordinator = CardIngestionCoordinator(
            scope = this,
            cardHydrationReady = cardHydrationReady,
            cardFlightDataFlow = cardFlightDataFlow,
            consumeBufferedCardSample = { bufferedSample?.also { bufferedSample = null } },
            unitsPreferencesFlow = unitsPreferencesFlow,
            initializeCardPreferences = { },
            startIndependentClock = { }
        )

        try {
            coordinator.bindCards(viewModel)

            cardHydrationReady.value = true
            advanceUntilIdle()

            verify(viewModel).updateCardsWithLiveData(expected)
        } finally {
            coordinator.stop()
        }
    }

    @Test
    fun propagatesUnitsPreferencesUpdates() = runTest {
        val cardHydrationReady = MutableStateFlow(true)
        val cardFlightDataFlow = MutableStateFlow<RealTimeFlightData?>(null)
        val unitsPreferencesFlow = MutableStateFlow(UnitsPreferences())
        val viewModel = mockFlightViewModel()
        val coordinator = CardIngestionCoordinator(
            scope = this,
            cardHydrationReady = cardHydrationReady,
            cardFlightDataFlow = cardFlightDataFlow,
            consumeBufferedCardSample = { null },
            unitsPreferencesFlow = unitsPreferencesFlow,
            initializeCardPreferences = { },
            startIndependentClock = { }
        )

        try {
            coordinator.bindCards(viewModel)
            advanceUntilIdle()

            val updated = UnitsPreferences(pressure = PressureUnit.INHG)
            unitsPreferencesFlow.value = updated
            advanceUntilIdle()

            verify(viewModel).updateUnitsPreferences(updated)
        } finally {
            coordinator.stop()
        }
    }

    @Test
    fun ignoresNullSamplesToPreserveLastValues() = runTest {
        val cardHydrationReady = MutableStateFlow(true)
        val cardFlightDataFlow = MutableStateFlow<RealTimeFlightData?>(null)
        val unitsPreferencesFlow = MutableStateFlow(UnitsPreferences())
        val viewModel = mockFlightViewModel()
        val coordinator = CardIngestionCoordinator(
            scope = this,
            cardHydrationReady = cardHydrationReady,
            cardFlightDataFlow = cardFlightDataFlow,
            consumeBufferedCardSample = { null },
            unitsPreferencesFlow = unitsPreferencesFlow,
            initializeCardPreferences = { },
            startIndependentClock = { }
        )

        try {
            coordinator.bindCards(viewModel)

            val sample = RealTimeFlightData(latitude = 3.0)
            cardFlightDataFlow.value = sample
            advanceUntilIdle()

            cardFlightDataFlow.value = null
            advanceUntilIdle()

            verify(viewModel, times(1)).updateCardsWithLiveData(sample)
        } finally {
            coordinator.stop()
        }
    }

    @Test
    fun forwardsActiveProfileAndVisibilitiesTogether() = runTest {
        val cardHydrationReady = MutableStateFlow(true)
        val cardFlightDataFlow = MutableStateFlow<RealTimeFlightData?>(null)
        val unitsPreferencesFlow = MutableStateFlow(UnitsPreferences())
        val activeProfileId = MutableStateFlow<String?>(null)
        val profileModeVisibilities =
            MutableStateFlow<Map<String, Map<FlightModeSelection, Boolean>>>(emptyMap())
        val visibilitiesHydrated = MutableStateFlow(true)
        val viewModel = mock<FlightDataViewModel>()
        whenever(viewModel.activeProfileId).thenReturn(activeProfileId)
        whenever(viewModel.profileModeVisibilities).thenReturn(profileModeVisibilities)
        whenever(viewModel.activeProfileModeVisibilitiesHydrated).thenReturn(visibilitiesHydrated)
        val forwarded =
            mutableListOf<Pair<String?, Map<String, Map<FlightModeSelection, Boolean>>>>()
        val coordinator = CardIngestionCoordinator(
            scope = this,
            cardHydrationReady = cardHydrationReady,
            cardFlightDataFlow = cardFlightDataFlow,
            consumeBufferedCardSample = { null },
            unitsPreferencesFlow = unitsPreferencesFlow,
            initializeCardPreferences = { },
            startIndependentClock = { },
            onProfileModeVisibilitiesChanged = { profileId, visibilities ->
                forwarded += profileId to visibilities
            }
        )

        try {
            coordinator.bindCards(viewModel)
            advanceUntilIdle()

            val visibilityUpdate = mapOf(
                "pilot-a" to mapOf(FlightModeSelection.THERMAL to false)
            )
            activeProfileId.value = "pilot-a"
            profileModeVisibilities.value = visibilityUpdate
            advanceUntilIdle()

            assertEquals("pilot-a", forwarded.last().first)
            assertEquals(visibilityUpdate, forwarded.last().second)
        } finally {
            coordinator.stop()
        }
    }

    @Test
    fun suppressesProfileVisibilitiesUntilHydrated() = runTest {
        val cardHydrationReady = MutableStateFlow(true)
        val cardFlightDataFlow = MutableStateFlow<RealTimeFlightData?>(null)
        val unitsPreferencesFlow = MutableStateFlow(UnitsPreferences())
        val activeProfileId = MutableStateFlow<String?>("pilot-a")
        val profileModeVisibilities = MutableStateFlow(
            mapOf("pilot-a" to mapOf(FlightModeSelection.THERMAL to false))
        )
        val visibilitiesHydrated = MutableStateFlow(false)
        val viewModel = mock<FlightDataViewModel>()
        whenever(viewModel.activeProfileId).thenReturn(activeProfileId)
        whenever(viewModel.profileModeVisibilities).thenReturn(profileModeVisibilities)
        whenever(viewModel.activeProfileModeVisibilitiesHydrated).thenReturn(visibilitiesHydrated)
        val forwarded =
            mutableListOf<Pair<String?, Map<String, Map<FlightModeSelection, Boolean>>>>()
        val coordinator = CardIngestionCoordinator(
            scope = this,
            cardHydrationReady = cardHydrationReady,
            cardFlightDataFlow = cardFlightDataFlow,
            consumeBufferedCardSample = { null },
            unitsPreferencesFlow = unitsPreferencesFlow,
            initializeCardPreferences = { },
            startIndependentClock = { },
            onProfileModeVisibilitiesChanged = { profileId, visibilities ->
                forwarded += profileId to visibilities
            }
        )

        try {
            coordinator.bindCards(viewModel)
            advanceUntilIdle()
            assertEquals(emptyList<Pair<String?, Map<String, Map<FlightModeSelection, Boolean>>>>(), forwarded)

            visibilitiesHydrated.value = true
            advanceUntilIdle()

            assertEquals(1, forwarded.size)
            assertEquals("pilot-a", forwarded.single().first)
            assertEquals(profileModeVisibilities.value, forwarded.single().second)
        } finally {
            coordinator.stop()
        }
    }

    private fun mockFlightViewModel(
        activeProfileId: MutableStateFlow<String?> = MutableStateFlow(null),
        profileModeVisibilities: MutableStateFlow<Map<String, Map<FlightModeSelection, Boolean>>> =
            MutableStateFlow(emptyMap()),
        activeProfileModeVisibilitiesHydrated: MutableStateFlow<Boolean> =
            MutableStateFlow(false)
    ): FlightDataViewModel {
        val viewModel = mock<FlightDataViewModel>()
        whenever(viewModel.activeProfileId).thenReturn(activeProfileId)
        whenever(viewModel.profileModeVisibilities).thenReturn(profileModeVisibilities)
        whenever(viewModel.activeProfileModeVisibilitiesHydrated)
            .thenReturn(activeProfileModeVisibilitiesHydrated)
        return viewModel
    }
}
