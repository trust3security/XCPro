package com.trust3.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AdsbTrafficStoreTierHysteresisTest {

    @Test
    fun select_keepsAmberStableAcrossNearBoundaryJitter() {
        val store = AdsbTrafficStore()
        val now = 1_000_000L
        val first = target(
            index = 91,
            lat = 0.0,
            lon = 0.04470,
            receivedMonoMs = now
        ).copy(trackDeg = null)
        val jitter1 = first.copy(
            lon = 0.04510,
            receivedMonoMs = now + 1_000L
        )
        val jitter2 = first.copy(
            lon = 0.04480,
            receivedMonoMs = now + 2_000L
        )
        val jitter3 = first.copy(
            lon = 0.04520,
            receivedMonoMs = now + 3_000L
        )

        store.upsertAll(listOf(first))
        val firstUi = selectAtZeroReference(store, nowMonoMs = now).displayed.first()

        store.upsertAll(listOf(jitter1))
        val jitter1Ui = selectAtZeroReference(store, nowMonoMs = now + 1_000L).displayed.first()

        store.upsertAll(listOf(jitter2))
        val jitter2Ui = selectAtZeroReference(store, nowMonoMs = now + 2_000L).displayed.first()

        store.upsertAll(listOf(jitter3))
        val jitter3Ui = selectAtZeroReference(store, nowMonoMs = now + 3_000L).displayed.first()

        assertEquals(AdsbProximityTier.AMBER, firstUi.proximityTier)
        assertEquals(AdsbProximityTier.AMBER, jitter1Ui.proximityTier)
        assertEquals(AdsbProximityTier.AMBER, jitter2Ui.proximityTier)
        assertEquals(AdsbProximityTier.AMBER, jitter3Ui.proximityTier)
    }

    @Test
    fun select_keepsRedStableAcrossNearBoundaryJitterWithoutPostPassEvidence() {
        val store = AdsbTrafficStore()
        val now = 1_010_000L
        val first = target(
            index = 92,
            lat = 0.0,
            lon = 0.01770,
            receivedMonoMs = now
        ).copy(trackDeg = null)
        val jitter1 = first.copy(
            lon = 0.01830,
            receivedMonoMs = now + 1_000L
        )
        val jitter2 = first.copy(
            lon = 0.01840,
            receivedMonoMs = now + 2_000L
        )

        store.upsertAll(listOf(first))
        val firstUi = selectAtZeroReference(store, nowMonoMs = now).displayed.first()

        store.upsertAll(listOf(jitter1))
        val jitter1Ui = selectAtZeroReference(store, nowMonoMs = now + 1_000L).displayed.first()

        store.upsertAll(listOf(jitter2))
        val jitter2Ui = selectAtZeroReference(store, nowMonoMs = now + 2_000L).displayed.first()

        assertEquals(AdsbProximityTier.RED, firstUi.proximityTier)
        assertEquals(AdsbProximityTier.RED, jitter1Ui.proximityTier)
        assertEquals(AdsbProximityTier.RED, jitter2Ui.proximityTier)
        assertFalse(jitter1Ui.isClosing)
        assertFalse(jitter2Ui.isClosing)
    }

    private fun selectAtZeroReference(
        store: AdsbTrafficStore,
        nowMonoMs: Long
    ): AdsbStoreSelection = store.select(
        nowMonoMs = nowMonoMs,
        queryCenterLat = 0.0,
        queryCenterLon = 0.0,
        referenceLat = 0.0,
        referenceLon = 0.0,
        ownshipAltitudeMeters = 1_000.0,
        usesOwnshipReference = true,
        radiusMeters = 20_000.0,
        verticalAboveMeters = 5_000.0,
        verticalBelowMeters = 5_000.0,
        maxDisplayed = 30,
        staleAfterSec = 60
    )
}
