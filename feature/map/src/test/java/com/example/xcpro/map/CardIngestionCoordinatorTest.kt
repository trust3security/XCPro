package com.example.xcpro.map

import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.units.PressureUnit
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class CardIngestionCoordinatorTest {

    @Test
    fun bindCards_isIdempotent() = runTest {
        val cardHydrationReady = MutableStateFlow(true)
        val cardFlightDataFlow = MutableStateFlow<RealTimeFlightData?>(null)
        val unitsPreferencesFlow = MutableStateFlow(UnitsPreferences())
        val viewModel = mock<FlightDataViewModel>()
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
        val viewModel = mock<FlightDataViewModel>()
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
        val viewModel = mock<FlightDataViewModel>()
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
        val viewModel = mock<FlightDataViewModel>()
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
}
