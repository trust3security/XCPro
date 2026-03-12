package com.example.dfcards

import com.example.dfcards.dfcards.CardState
import com.example.dfcards.dfcards.FlightCardsUseCaseFactory
import com.example.dfcards.dfcards.FlightDataTemplateManagerFactory
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.core.common.geometry.IntSizePx
import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FlightDataViewModelProfileLayoutTest {

    @get:Rule
    private val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun quickProfileSwitch_restoresTargetProfileCardPositionAndSavesSourceScope() = runTest(mainDispatcherRule.dispatcher) {
        val template = FlightTemplate(
            id = "profile-layout",
            name = "Profile Layout",
            description = "Single-card layout",
            cardIds = listOf("agl")
        )
        val preferences = mock<CardPreferences>()
        stubCommonPreferences(preferences, template)
        whenever(preferences.getProfileCardPositions("pilot-a", FlightModeSelection.CRUISE.name)).thenReturn(
            flowOf(
                mapOf(
                    "agl" to CardPreferences.CardPosition(
                        x = 12f,
                        y = 24f,
                        width = 180f,
                        height = 90f
                    )
                )
            )
        )
        whenever(preferences.getProfileCardPositions("pilot-b", FlightModeSelection.CRUISE.name)).thenReturn(
            flowOf(
                mapOf(
                    "agl" to CardPreferences.CardPosition(
                        x = 220f,
                        y = 340f,
                        width = 180f,
                        height = 90f
                    )
                )
            )
        )

        val viewModel = buildViewModel(preferences)
        advanceUntilIdle()

        viewModel.setProfileTemplate("pilot-a", FlightModeSelection.CRUISE, template.id)
        viewModel.setProfileCards("pilot-a", FlightModeSelection.CRUISE, template.cardIds)
        viewModel.setProfileTemplate("pilot-b", FlightModeSelection.CRUISE, template.id)
        viewModel.setProfileCards("pilot-b", FlightModeSelection.CRUISE, template.cardIds)

        viewModel.prepareCardsForProfile(
            profileId = "pilot-a",
            flightMode = FlightModeSelection.CRUISE,
            containerSize = TEST_CONTAINER,
            density = TEST_DENSITY
        )
        assertCardPosition(
            viewModel = viewModel,
            expectedX = 12f,
            expectedY = 24f
        )

        val movedCard = requireNotNull(viewModel.getCardState("agl")).copy(
            x = 48f,
            y = 96f
        )
        viewModel.updateCardState(movedCard)

        viewModel.prepareCardsForProfile(
            profileId = "pilot-b",
            flightMode = FlightModeSelection.CRUISE,
            containerSize = TEST_CONTAINER,
            density = TEST_DENSITY
        )
        advanceUntilIdle()

        assertCardPosition(
            viewModel = viewModel,
            expectedX = 220f,
            expectedY = 340f
        )
        verify(preferences).saveProfileCardPositions(
            eq("pilot-a"),
            eq(FlightModeSelection.CRUISE.name),
            argThat<List<CardState>> { size == 1 && first().id == "agl" && first().x == 48f && first().y == 96f }
        )
    }

    private fun buildViewModel(preferences: CardPreferences): FlightDataViewModel {
        val viewModel = FlightDataViewModel(
            ioDispatcher = Dispatchers.Unconfined,
            cardsUseCaseFactory = FlightCardsUseCaseFactory(FakeClock()),
            templateManagerFactory = FlightDataTemplateManagerFactory()
        )
        viewModel.initializeCardPreferences(preferences)
        return viewModel
    }

    private fun stubCommonPreferences(
        preferences: CardPreferences,
        template: FlightTemplate
    ) {
        whenever(preferences.getAllTemplates()).thenReturn(flowOf(listOf(template)))
        whenever(preferences.getAllProfileFlightModeTemplates()).thenReturn(flowOf(emptyMap()))
        whenever(preferences.getAllProfileTemplateCards()).thenReturn(flowOf(emptyMap()))
        whenever(preferences.getCardsAcrossPortrait()).thenReturn(
            flowOf(CardPreferences.DEFAULT_CARDS_ACROSS_PORTRAIT)
        )
        whenever(preferences.getCardsAnchorPortrait()).thenReturn(
            flowOf(CardPreferences.DEFAULT_ANCHOR_PORTRAIT)
        )
        whenever(preferences.getProfileAllFlightModeVisibilities(any())).thenReturn(flowOf(emptyMap()))
        whenever(preferences.getProfileCardPositions(any(), any())).thenReturn(flowOf(emptyMap()))
        whenever(preferences.getCardPosition(any())).thenReturn(flowOf(null))
    }

    private fun assertCardPosition(
        viewModel: FlightDataViewModel,
        expectedX: Float,
        expectedY: Float
    ) {
        val cardState = requireNotNull(viewModel.getCardState("agl"))
        assertEquals(expectedX, cardState.x, 0.001f)
        assertEquals(expectedY, cardState.y, 0.001f)
    }

    private companion object {
        val TEST_CONTAINER = IntSizePx(width = 1080, height = 1920)
        val TEST_DENSITY = DensityScale(density = 1f, fontScale = 1f)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
