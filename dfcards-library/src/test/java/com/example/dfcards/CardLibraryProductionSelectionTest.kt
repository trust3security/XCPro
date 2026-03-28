package com.example.dfcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLibraryProductionSelectionTest {

    @Test
    fun navigation_selection_ships_only_release_grade_glide_and_waypoint_cards() {
        assertEquals(
            listOf("track", "wpt_dist", "wpt_brg", "final_gld", "arr_alt", "req_alt", "arr_mc0", "wpt_eta"),
            CardLibrary.getCardsByCategory(CardCategory.NAVIGATION).map { it.id }
        )
    }

    @Test
    fun competition_selection_ships_only_release_grade_task_cards() {
        assertEquals(
            listOf("task_spd", "task_dist", "task_remain_dist", "task_remain_time", "start_alt"),
            CardLibrary.getCardsByCategory(CardCategory.COMPETITION).map { it.id }
        )
    }

    @Test
    fun search_offers_only_release_grade_waypoint_cards() {
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
    fun glide_computer_production_contract_matches_the_final_release_scope() {
        val productionIds = buildSet {
            addAll(CardLibrary.getCardsByCategory(CardCategory.ESSENTIAL).map { it.id })
            addAll(CardLibrary.getCardsByCategory(CardCategory.NAVIGATION).map { it.id })
            addAll(CardLibrary.getCardsByCategory(CardCategory.PERFORMANCE).map { it.id })
            addAll(CardLibrary.getCardsByCategory(CardCategory.COMPETITION).map { it.id })
        }

        val shippedGlideComputerIds = setOf(
            "ias",
            "tas",
            "ground_speed",
            "ld_curr",
            "polar_ld",
            "best_ld",
            "netto",
            "netto_avg30",
            "mc_speed",
            "final_gld",
            "arr_alt",
            "req_alt",
            "arr_mc0",
            "wpt_dist",
            "wpt_brg",
            "wpt_eta",
            "task_spd",
            "task_dist",
            "task_remain_dist",
            "task_remain_time",
            "start_alt"
        )

        assertEquals(shippedGlideComputerIds, productionIds.intersect(shippedGlideComputerIds))
        assertTrue("final distance stays uncataloged in Phase 5", "final_dist" !in productionIds)
    }

    @Test
    fun full_known_card_registry_keeps_glide_computer_ids_for_layout_compatibility() {
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
