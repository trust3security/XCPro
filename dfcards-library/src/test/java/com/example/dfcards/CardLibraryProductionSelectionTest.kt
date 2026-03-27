package com.example.dfcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLibraryProductionSelectionTest {

    @Test
    fun navigation_selection_hides_placeholder_waypoint_cards_but_keeps_implemented_glide_cards() {
        assertEquals(
            listOf("track", "final_gld", "arr_alt", "req_alt", "arr_mc0"),
            CardLibrary.getCardsByCategory(CardCategory.NAVIGATION).map { it.id }
        )
    }

    @Test
    fun competition_selection_hides_placeholder_cards_until_runtime_authority_exists() {
        assertEquals(
            emptyList<String>(),
            CardLibrary.getCardsByCategory(CardCategory.COMPETITION).map { it.id }
        )
    }

    @Test
    fun search_does_not_offer_hidden_placeholder_cards() {
        assertEquals(
            emptyList<String>(),
            CardLibrary.searchCards("waypoint").map { it.id }
        )
        assertEquals(
            listOf("final_gld"),
            CardLibrary.searchCards("final glide").map { it.id }
        )
    }

    @Test
    fun full_known_card_registry_still_keeps_hidden_cards_for_layout_compatibility() {
        val knownIds = CardLibrary.allCards.map { it.id }.toSet()

        assertTrue(CardId.WPT_DIST in knownIds)
        assertTrue(CardId.WPT_BRG in knownIds)
        assertTrue(CardId.WPT_ETA in knownIds)
        assertTrue(CardId.TASK_SPD in knownIds)
        assertTrue(CardId.TASK_DIST in knownIds)
        assertTrue(CardId.START_ALT in knownIds)
    }
}
