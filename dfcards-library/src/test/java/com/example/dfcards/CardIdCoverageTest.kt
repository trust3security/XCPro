package com.example.dfcards

// Coverage tests for card ID catalogs, templates, and spec registry.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardIdCoverageTest {

    @Test
    fun cardDefinitions_match_knownIds() {
        val catalogIds = allCardDefinitions.map { it.id }.toSet()
        assertEquals(CardId.knownIds, catalogIds)
    }

    @Test
    fun cardDefinitions_preserve_known_order() {
        val expectedOrder = KnownCardId.values().map { it.raw }
        assertEquals(expectedOrder, allCardDefinitions.map { it.id })
    }

    @Test
    fun knownIds_match_enum() {
        val enumIds = KnownCardId.values().map { it.raw }.toSet()
        assertEquals(enumIds, CardId.knownIds)
    }

    @Test
    fun cardsByCategory_preserve_expected_order() {
        val expectedCategoryOrder = listOf(
            CardCategory.ESSENTIAL,
            CardCategory.VARIO,
            CardCategory.NAVIGATION,
            CardCategory.PERFORMANCE,
            CardCategory.TIME_WEATHER,
            CardCategory.COMPETITION,
            CardCategory.ADVANCED
        )
        assertEquals(expectedCategoryOrder, cardCatalogSections.map { it.first })
        assertEquals(expectedCategoryOrder, cardsByCategory.keys.toList())

        assertEquals(
            listOf("gps_alt", "baro_alt", "agl", "vario", "ias", "tas", "ground_speed"),
            cardsByCategory.getValue(CardCategory.ESSENTIAL).map { it.id }
        )
        assertEquals(
            listOf(
                "vario_optimized",
                "vario_legacy",
                "vario_raw",
                "vario_gps",
                "vario_complementary",
                "hawk_vario",
                "real_igc_vario"
            ),
            cardsByCategory.getValue(CardCategory.VARIO).map { it.id }
        )
        assertEquals(
            listOf(
                "track",
                "wpt_dist",
                "wpt_brg",
                "final_gld",
                "arr_alt",
                "req_alt",
                "arr_mc0",
                "wpt_eta"
            ),
            cardsByCategory.getValue(CardCategory.NAVIGATION).map { it.id }
        )
        assertEquals(
            listOf(
                "thermal_avg",
                "thermal_tc_avg",
                "thermal_t_avg",
                "thermal_tc_gain",
                "netto",
                "netto_avg30",
                "levo_netto",
                "ld_curr",
                "ld_vario",
                "polar_ld",
                "best_ld",
                "mc_speed"
            ),
            cardsByCategory.getValue(CardCategory.PERFORMANCE).map { it.id }
        )
        assertEquals(
            listOf("wind_spd", "wind_dir", "wind_arrow", "local_time", "flight_time"),
            cardsByCategory.getValue(CardCategory.TIME_WEATHER).map { it.id }
        )
        assertEquals(
            listOf("task_spd", "task_dist", "task_remain_dist", "task_remain_time", "start_alt"),
            cardsByCategory.getValue(CardCategory.COMPETITION).map { it.id }
        )
        assertEquals(
            listOf("g_force", "flarm", "qnh", "satelites", "gps_accuracy"),
            cardsByCategory.getValue(CardCategory.ADVANCED).map { it.id }
        )
    }

    @Test
    fun templates_use_known_ids() {
        val templateIds = FlightTemplates.getDefaultTemplates()
            .flatMap { it.cardIds }
            .toSet()
        assertTrue(templateIds.all { it in CardId.knownIds })
    }

    @Test
    fun specs_cover_all_known_ids() {
        val expected = KnownCardId.values().toSet()
        assertEquals(expected, CardFormatSpecs.specs.keys)
    }
}
