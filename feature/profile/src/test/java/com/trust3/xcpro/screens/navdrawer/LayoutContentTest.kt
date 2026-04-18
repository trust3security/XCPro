package com.trust3.xcpro.screens.navdrawer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.dfcards.CardPreferences
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LayoutContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardsAcrossLabel_reflectsUiState() {
        setContent(
            LayoutUiState(
                cardsAcrossPortrait = 6,
                anchorPortrait = CardPreferences.CardAnchor.TOP
            )
        )

        composeRule.onNodeWithText("Cards across (portrait): 6").assertIsDisplayed()
        composeRule.onNodeWithText("Cards start at the top edge with no gap.").assertIsDisplayed()
        composeRule.onNodeWithTag(LAYOUT_TAG_CARDS_ACROSS_SLIDER).assertIsDisplayed()
    }

    @Test
    fun bottomAnchorButton_invokesCallback() {
        var selectedAnchor = CardPreferences.DEFAULT_ANCHOR_PORTRAIT
        setContent(
            LayoutUiState(
                cardsAcrossPortrait = CardPreferences.DEFAULT_CARDS_ACROSS_PORTRAIT,
                anchorPortrait = CardPreferences.CardAnchor.TOP
            ),
            onSetAnchorPortrait = { selectedAnchor = it }
        )

        composeRule.onNodeWithTag(LAYOUT_TAG_ANCHOR_BOTTOM_BUTTON).performClick()

        assertEquals(CardPreferences.CardAnchor.BOTTOM, selectedAnchor)
    }

    private fun setContent(
        uiState: LayoutUiState,
        onSetCardsAcrossPortrait: (Int) -> Unit = {},
        onSetAnchorPortrait: (CardPreferences.CardAnchor) -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                LayoutContent(
                    uiState = uiState,
                    onSetCardsAcrossPortrait = onSetCardsAcrossPortrait,
                    onSetAnchorPortrait = onSetAnchorPortrait
                )
            }
        }
    }
}
