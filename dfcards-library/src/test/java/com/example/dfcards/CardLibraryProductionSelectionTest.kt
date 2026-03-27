package com.example.dfcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLibraryProductionSelectionTest {

    @Test
    fun navigation_selection_restores_authoritative_waypoint_cards_and_keeps_glide_cards() {
        assertEquals(
            listOf("track", "wpt_dist", "wpt_brg", "final_gld", "arr_alt", "req_alt", "arr_mc0", "wpt_eta"),
            CardLibrary.getCardsByCategory(CardCategory.NAVIGATION).map { it.id }
        )
    }

    @Test
    fun competition_selection_restores_authoritative_task_cards() {
        assertEquals(
            listOf("task_spd", "task_dist", "task_remain_dist", "task_remain_time", "start_alt"),
            CardLibrary.getCardsByCategory(CardCategory.COMPETITION).map { it.id }
        )
    }

    @Test
    fun search_offers_only_authoritative_waypoint_cards() {
        assertEquals(
            listOf("wpt_dist", "wpt_brg", "wpt_eta"),
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
        assertTrue(CardId.TASK_REMAIN_DIST in knownIds)
        assertTrue(CardId.TASK_REMAIN_TIME in knownIds)
        assertTrue(CardId.START_ALT in knownIds)
    }
}
