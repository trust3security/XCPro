package com.example.dfcards

import com.example.dfcards.dfcards.FlightCardsUseCaseFactory
import com.example.dfcards.dfcards.FlightDataTemplateManagerFactory
import com.example.dfcards.dfcards.FlightDataViewModel
import com.trust3.xcpro.core.time.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FlightDataViewModelVisibilityHydrationTest {

    @get:Rule
    val mainDispatcherRule = DfCardsMainDispatcherRule()

    @Test
    fun initializeCardPreferences_marksCurrentActiveProfileHydrated() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = mock<CardPreferences>()
        stubCommonPreferences(preferences)
        whenever(preferences.getProfileAllFlightModeVisibilities("pilot-a"))
            .thenReturn(flowOf(mapOf(FlightModeSelection.THERMAL.name to false)))

        val viewModel = buildViewModel()
        viewModel.setActiveProfile("pilot-a")
        assertFalse(viewModel.activeProfileModeVisibilitiesHydrated.value)

        viewModel.initializeCardPreferences(preferences)
        advanceUntilIdle()

        assertTrue(viewModel.activeProfileModeVisibilitiesHydrated.value)
        assertEquals(false, viewModel.flightModeVisibilitiesFor("pilot-a")[FlightModeSelection.THERMAL])
    }

    @Test
    fun setActiveProfile_loadsVisibilitiesForProfileMissingFromInitialHydration() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = mock<CardPreferences>()
        stubCommonPreferences(preferences)
        whenever(preferences.getProfileAllFlightModeVisibilities("pilot-b"))
            .thenReturn(flowOf(mapOf(FlightModeSelection.THERMAL.name to false)))

        val viewModel = buildViewModel()
        viewModel.initializeCardPreferences(preferences)
        advanceUntilIdle()

        assertFalse(viewModel.activeProfileModeVisibilitiesHydrated.value)

        viewModel.setActiveProfile("pilot-b")
        assertFalse(viewModel.activeProfileModeVisibilitiesHydrated.value)
        advanceUntilIdle()

        assertTrue(viewModel.activeProfileModeVisibilitiesHydrated.value)
        assertEquals(false, viewModel.flightModeVisibilitiesFor("pilot-b")[FlightModeSelection.THERMAL])
    }

    @Test
    fun switchingActiveProfile_ignoresStaleVisibilityLoadForOldProfile() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = mock<CardPreferences>()
        stubCommonPreferences(preferences)
        whenever(preferences.getProfileAllFlightModeVisibilities(any())).thenAnswer { invocation ->
            when (invocation.arguments[0] as String) {
                "pilot-a" -> flow {
                    delay(100)
                    emit(mapOf(FlightModeSelection.THERMAL.name to false))
                }
                "pilot-b" -> flowOf(mapOf(FlightModeSelection.FINAL_GLIDE.name to false))
                else -> flowOf(emptyMap<String, Boolean>())
            }
        }

        val viewModel = buildViewModel()
        viewModel.initializeCardPreferences(preferences)
        advanceUntilIdle()

        viewModel.setActiveProfile("pilot-a")
        advanceTimeBy(50)
        viewModel.setActiveProfile("pilot-b")
        advanceUntilIdle()

        assertEquals("pilot-b", viewModel.activeProfileId.value)
        assertTrue(viewModel.activeProfileModeVisibilitiesHydrated.value)
        assertEquals(false, viewModel.flightModeVisibilitiesFor("pilot-b")[FlightModeSelection.FINAL_GLIDE])
        assertEquals(true, viewModel.flightModeVisibilitiesFor("pilot-a")[FlightModeSelection.THERMAL])
    }

    private fun buildViewModel(): FlightDataViewModel =
        FlightDataViewModel(
            ioDispatcher = mainDispatcherRule.dispatcher,
            cardsUseCaseFactory = FlightCardsUseCaseFactory(FakeClock()),
            templateManagerFactory = FlightDataTemplateManagerFactory()
        )

    private fun stubCommonPreferences(preferences: CardPreferences) {
        whenever(preferences.getAllTemplates()).thenReturn(flowOf(emptyList()))
        whenever(preferences.getAllProfileFlightModeTemplates()).thenReturn(flowOf(emptyMap()))
        whenever(preferences.getAllProfileTemplateCards()).thenReturn(flowOf(emptyMap()))
        whenever(preferences.getProfileAllFlightModeVisibilities(any())).thenReturn(flowOf(emptyMap()))
        whenever(preferences.getCardsAcrossPortrait()).thenReturn(
            flowOf(CardPreferences.DEFAULT_CARDS_ACROSS_PORTRAIT)
        )
        whenever(preferences.getCardsAnchorPortrait()).thenReturn(
            flowOf(CardPreferences.DEFAULT_ANCHOR_PORTRAIT)
        )
        whenever(preferences.getProfileCardPositions(any(), any())).thenReturn(flowOf(emptyMap()))
        whenever(preferences.getCardPosition(any())).thenReturn(flowOf(null))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DfCardsMainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

