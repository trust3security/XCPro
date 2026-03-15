package com.example.xcpro.screens.navdrawer

import com.example.dfcards.CardPreferences
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LayoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_reflectsCardPreferenceUpdates() = runTest {
        val cardPreferences = mock<CardPreferences>()
        val cardsAcross = MutableStateFlow(CardPreferences.DEFAULT_CARDS_ACROSS_PORTRAIT)
        val anchor = MutableStateFlow(CardPreferences.DEFAULT_ANCHOR_PORTRAIT)
        whenever(cardPreferences.getCardsAcrossPortrait()).thenReturn(cardsAcross)
        whenever(cardPreferences.getCardsAnchorPortrait()).thenReturn(anchor)
        val viewModel = LayoutViewModel(LayoutPreferencesUseCase(cardPreferences))
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        cardsAcross.value = 6
        anchor.value = CardPreferences.CardAnchor.BOTTOM
        advanceUntilIdle()

        assertEquals(6, viewModel.uiState.value.cardsAcrossPortrait)
        assertEquals(CardPreferences.CardAnchor.BOTTOM, viewModel.uiState.value.anchorPortrait)
        collector.cancel()
    }

    @Test
    fun setters_writeThroughToCardPreferences() = runTest {
        val cardPreferences = mock<CardPreferences>()
        whenever(cardPreferences.getCardsAcrossPortrait()).thenReturn(
            MutableStateFlow(CardPreferences.DEFAULT_CARDS_ACROSS_PORTRAIT)
        )
        whenever(cardPreferences.getCardsAnchorPortrait()).thenReturn(
            MutableStateFlow(CardPreferences.DEFAULT_ANCHOR_PORTRAIT)
        )
        val viewModel = LayoutViewModel(LayoutPreferencesUseCase(cardPreferences))

        viewModel.setCardsAcrossPortrait(5)
        viewModel.setAnchorPortrait(CardPreferences.CardAnchor.BOTTOM)
        advanceUntilIdle()

        verify(cardPreferences).setCardsAcrossPortrait(5)
        verify(cardPreferences).setCardsAnchorPortrait(CardPreferences.CardAnchor.BOTTOM)
    }
}
