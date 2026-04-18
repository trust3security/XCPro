package com.trust3.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbTrafficStoreTrendTransitionsTest {

    @Test
    fun select_deEscalatesRedToAmberWhenNoLongerClosingAfterRecoveryDwell() {
        val store = AdsbTrafficStore()
        val now = 410_000L
        val targetId = target(
            index = 5,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(trackDeg = null)
        val closer = targetId.copy(
            lon = 151.2140,
            receivedMonoMs = now + 2_000L
        )
        val sameDistance = closer.copy(receivedMonoMs = now + 5_000L)
        store.upsertAll(listOf(targetId))
        store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )
        store.upsertAll(listOf(closer))
        val closingSelection = store.select(
            nowMonoMs = now + 2_000L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )
        store.upsertAll(listOf(sameDistance))
        val recoveringSelection = store.select(
            nowMonoMs = now + 5_000L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )
        val divergingAfterDwell = sameDistance.copy(
            lon = 151.2143,
            receivedMonoMs = now + 9_200L
        )
        store.upsertAll(listOf(divergingAfterDwell))
        val deEscalatedSelection = store.select(
            nowMonoMs = now + 9_200L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        val closingUi = closingSelection.displayed.first()
        val recoveringUi = recoveringSelection.displayed.first()
        val deEscalatedUi = deEscalatedSelection.displayed.first()
        assertEquals(AdsbProximityTier.RED, closingUi.proximityTier)
        assertEquals(AdsbProximityReason.APPROACH_CLOSING, closingUi.proximityReason)
        assertEquals(AdsbProximityTier.RED, recoveringUi.proximityTier)
        assertEquals(AdsbProximityReason.RECOVERY_DWELL, recoveringUi.proximityReason)
        assertEquals(AdsbProximityTier.AMBER, deEscalatedUi.proximityTier)
        assertEquals(AdsbProximityReason.DIVERGING_OR_STEADY, deEscalatedUi.proximityReason)
    }

    @Test
    fun select_deEscalatesRedToGreenOnSecondFreshPostPassSample() {
        val store = AdsbTrafficStore()
        val now = 412_000L
        val far = target(
            index = 6,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(trackDeg = null)
        val closing = far.copy(
            lon = 151.2140,
            receivedMonoMs = now + 2_000L
        )
        val sameDistance = closing.copy(receivedMonoMs = now + 5_000L)
        val firstPostPass = sameDistance.copy(
            lon = 151.2143,
            receivedMonoMs = now + 9_200L
        )
        val secondPostPass = firstPostPass.copy(
            lon = 151.2146,
            receivedMonoMs = now + 10_400L
        )

        store.upsertAll(listOf(far))
        selectAt(store = store, nowMonoMs = now)

        store.upsertAll(listOf(closing))
        val closingUi = selectAt(store = store, nowMonoMs = now + 2_000L).displayed.first()

        store.upsertAll(listOf(sameDistance))
        selectAt(store = store, nowMonoMs = now + 5_000L)

        store.upsertAll(listOf(firstPostPass))
        val firstPostPassUi = selectAt(store = store, nowMonoMs = now + 9_200L).displayed.first()

        store.upsertAll(listOf(secondPostPass))
        val secondPostPassUi = selectAt(store = store, nowMonoMs = now + 10_400L).displayed.first()

        assertEquals(AdsbProximityTier.RED, closingUi.proximityTier)
        assertTrue(closingUi.isClosing)
        assertEquals(AdsbProximityTier.AMBER, firstPostPassUi.proximityTier)
        assertFalse(firstPostPassUi.isClosing)
        assertEquals(AdsbProximityTier.GREEN, secondPostPassUi.proximityTier)
        assertFalse(secondPostPassUi.isClosing)
    }

    @Test
    fun select_keepsAmberWhenNeverClosingEvenWithFreshSamples() {
        val store = AdsbTrafficStore()
        val now = 414_000L
        val first = target(
            index = 7,
            lat = -33.8688,
            lon = 151.2420,
            receivedMonoMs = now
        ).copy(trackDeg = null)
        val second = first.copy(
            lon = 151.2421,
            receivedMonoMs = now + 2_000L
        )

        store.upsertAll(listOf(first))
        val firstUi = selectAt(store = store, nowMonoMs = now).displayed.first()

        store.upsertAll(listOf(second))
        val secondUi = selectAt(store = store, nowMonoMs = now + 2_000L).displayed.first()

        assertEquals(AdsbProximityTier.AMBER, firstUi.proximityTier)
        assertFalse(firstUi.isClosing)
        assertEquals(AdsbProximityTier.AMBER, secondUi.proximityTier)
        assertFalse(secondUi.isClosing)
        assertEquals(AdsbProximityReason.DIVERGING_OR_STEADY, secondUi.proximityReason)
    }

    @Test
    fun select_deEscalatesAmberToGreenAfterClosestApproachPassEvenWithoutClosingEpisode() {
        val store = AdsbTrafficStore()
        val now = 415_000L
        val first = target(
            index = 41,
            lat = -33.8688,
            lon = 151.24650,
            receivedMonoMs = now
        ).copy(trackDeg = null)
        val slightApproach = first.copy(
            lon = 151.24649,
            receivedMonoMs = now + 2_000L
        )
        val divergingPastClosest = first.copy(
            lon = 151.24799,
            receivedMonoMs = now + 4_000L
        )

        store.upsertAll(listOf(first))
        val firstUi = selectAt(store = store, nowMonoMs = now).displayed.first()

        store.upsertAll(listOf(slightApproach))
        val slightApproachUi = selectAt(store = store, nowMonoMs = now + 2_000L).displayed.first()

        store.upsertAll(listOf(divergingPastClosest))
        val passedUi = selectAt(store = store, nowMonoMs = now + 4_000L).displayed.first()

        assertEquals(AdsbProximityTier.AMBER, firstUi.proximityTier)
        assertEquals(AdsbProximityTier.AMBER, slightApproachUi.proximityTier)
        assertFalse(slightApproachUi.isClosing)
        assertEquals(AdsbProximityTier.GREEN, passedUi.proximityTier)
        assertFalse(passedUi.isClosing)
        assertEquals(AdsbProximityReason.DIVERGING_OR_STEADY, passedUi.proximityReason)
    }

    @Test
    fun select_staleSampleDoesNotReboundGreenBackToAmberAfterFreshPostPassDeEscalation() {
        val store = AdsbTrafficStore()
        val now = 418_000L
        val first = target(
            index = 47,
            lat = -33.8688,
            lon = 151.24650,
            receivedMonoMs = now
        ).copy(trackDeg = null)
        val slightApproach = first.copy(
            lon = 151.24649,
            receivedMonoMs = now + 2_000L
        )
        val divergingPastClosest = first.copy(
            lon = 151.24799,
            receivedMonoMs = now + 4_000L
        )

        store.upsertAll(listOf(first))
        selectAt(store = store, nowMonoMs = now)

        store.upsertAll(listOf(slightApproach))
        selectAt(store = store, nowMonoMs = now + 2_000L)

        store.upsertAll(listOf(divergingPastClosest))
        val freshGreenUi = selectAt(store = store, nowMonoMs = now + 4_000L).displayed.first()
        val staleRecheckUi = selectAt(store = store, nowMonoMs = now + 4_500L).displayed.first()

        assertEquals(AdsbProximityTier.GREEN, freshGreenUi.proximityTier)
        assertEquals(AdsbProximityTier.GREEN, staleRecheckUi.proximityTier)
        assertFalse(staleRecheckUi.isClosing)
    }

    @Test
    fun select_reEntersRedWhenClosingResumesAfterAmberDeEscalation() {
        val store = AdsbTrafficStore()
        val now = 600_000L
        val far = target(
            index = 40,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(trackDeg = null)
        val closing = far.copy(
            lon = 151.2140,
            receivedMonoMs = now + 2_000L
        )
        val diverging = far.copy(
            lon = 151.2145,
            receivedMonoMs = now + 3_000L
        )
        val reClosingNoise = far.copy(
            lon = 151.2130,
            receivedMonoMs = now + 8_300L
        )
        val reClosing = far.copy(
            lon = 151.2125,
            receivedMonoMs = now + 9_400L
        )

        store.upsertAll(listOf(far))
        selectAt(store = store, nowMonoMs = now)

        store.upsertAll(listOf(closing))
        val closingUi = selectAt(store = store, nowMonoMs = now + 2_000L).displayed.first()

        store.upsertAll(listOf(diverging))
        val recoveringUi = selectAt(store = store, nowMonoMs = now + 3_000L).displayed.first()
        val divergingAfterDwell = diverging.copy(
            lon = 151.2147,
            receivedMonoMs = now + 7_200L
        )
        store.upsertAll(listOf(divergingAfterDwell))
        val deEscalatedUi = selectAt(store = store, nowMonoMs = now + 7_200L).displayed.first()

        store.upsertAll(listOf(reClosingNoise))
        val reClosingNoiseUi = selectAt(store = store, nowMonoMs = now + 8_300L).displayed.first()

        store.upsertAll(listOf(reClosing))
        val reClosingUi = selectAt(store = store, nowMonoMs = now + 9_400L).displayed.first()

        assertEquals(AdsbProximityTier.RED, closingUi.proximityTier)
        assertTrue(closingUi.isClosing)
        assertEquals(AdsbProximityTier.RED, recoveringUi.proximityTier)
        assertFalse(recoveringUi.isClosing)
        assertEquals(AdsbProximityTier.AMBER, deEscalatedUi.proximityTier)
        assertFalse(deEscalatedUi.isClosing)
        assertEquals(AdsbProximityTier.GREEN, reClosingNoiseUi.proximityTier)
        assertFalse(reClosingNoiseUi.isClosing)
        assertEquals(AdsbProximityTier.RED, reClosingUi.proximityTier)
        assertTrue(reClosingUi.isClosing)
    }

    @Test
    fun select_deEscalatesAmberToGreenWhenNoLongerClosingAfterRecoveryDwell() {
        val store = AdsbTrafficStore()
        val now = 620_000L
        val far = target(
            index = 43,
            lat = -33.8688,
            lon = 151.2750,
            receivedMonoMs = now
        ).copy(trackDeg = null)
        val amberClosing = far.copy(
            lon = 151.2420,
            receivedMonoMs = now + 2_000L
        )
        val sameDistance = amberClosing.copy(receivedMonoMs = now + 5_000L)

        store.upsertAll(listOf(far))
        selectAt(store = store, nowMonoMs = now)

        store.upsertAll(listOf(amberClosing))
        val closingUi = selectAt(store = store, nowMonoMs = now + 2_000L).displayed.first()

        store.upsertAll(listOf(sameDistance))
        val recoveringUi = selectAt(store = store, nowMonoMs = now + 5_000L).displayed.first()
        val divergingAfterDwell = sameDistance.copy(
            lon = 151.2422,
            receivedMonoMs = now + 9_200L
        )
        store.upsertAll(listOf(divergingAfterDwell))
        val deEscalatedUi = selectAt(store = store, nowMonoMs = now + 9_200L).displayed.first()

        assertEquals(AdsbProximityTier.AMBER, closingUi.proximityTier)
        assertTrue(closingUi.isClosing)
        assertEquals(AdsbProximityTier.AMBER, recoveringUi.proximityTier)
        assertFalse(recoveringUi.isClosing)
        assertEquals(AdsbProximityTier.GREEN, deEscalatedUi.proximityTier)
        assertFalse(deEscalatedUi.isClosing)
    }

    @Test
    fun select_doesNotDeEscalateAmberWithoutFreshTargetSample() {
        val store = AdsbTrafficStore()
        val now = 625_000L
        val amberCandidate = target(
            index = 44,
            lat = -33.8688,
            lon = 151.2420,
            receivedMonoMs = now
        ).copy(trackDeg = null)

        store.upsertAll(listOf(amberCandidate))
        val firstSelection = selectAt(store = store, nowMonoMs = now).displayed.first()
        val staleSelection = selectAt(store = store, nowMonoMs = now + 5_000L).displayed.first()

        assertEquals(AdsbProximityTier.AMBER, firstSelection.proximityTier)
        assertFalse(firstSelection.isClosing)
        assertEquals(AdsbProximityTier.AMBER, staleSelection.proximityTier)
        assertFalse(staleSelection.isClosing)
    }

    @Test
    fun select_recoveryDwellDoesNotDeEscalateWithoutFreshSample() {
        val store = AdsbTrafficStore()
        val now = 640_000L
        val far = target(
            index = 45,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(trackDeg = null)
        val closing = far.copy(
            lon = 151.2140,
            receivedMonoMs = now + 2_000L
        )
        val sameDistance = closing.copy(receivedMonoMs = now + 3_000L)

        store.upsertAll(listOf(far))
        selectAt(store = store, nowMonoMs = now)

        store.upsertAll(listOf(closing))
        val closingUi = selectAt(store = store, nowMonoMs = now + 2_000L).displayed.first()

        store.upsertAll(listOf(sameDistance))
        val recoveringUi = selectAt(store = store, nowMonoMs = now + 3_000L).displayed.first()
        val noFreshSampleUi = selectAt(store = store, nowMonoMs = now + 7_500L).displayed.first()

        assertEquals(AdsbProximityTier.RED, closingUi.proximityTier)
        assertTrue(closingUi.isClosing)
        assertEquals(AdsbProximityTier.RED, recoveringUi.proximityTier)
        assertFalse(recoveringUi.isClosing)
        assertEquals(AdsbProximityReason.RECOVERY_DWELL, recoveringUi.proximityReason)
        assertEquals(AdsbProximityTier.RED, noFreshSampleUi.proximityTier)
        assertFalse(noFreshSampleUi.isClosing)
        assertEquals(AdsbProximityReason.DIVERGING_OR_STEADY, noFreshSampleUi.proximityReason)
    }

    @Test
    fun select_usesOwnshipReferenceSampleToRefreshTrendWhenTargetTimestampIsUnchanged() {
        val store = AdsbTrafficStore()
        val now = 650_000L
        val far = target(
            index = 46,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(trackDeg = null)
        val closing = far.copy(
            lon = 151.2140,
            receivedMonoMs = now + 2_000L
        )

        store.upsertAll(listOf(far))
        store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            referenceSampleMonoMs = now,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        store.upsertAll(listOf(closing))
        val closingUi = store.select(
            nowMonoMs = now + 2_000L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            referenceSampleMonoMs = now + 2_000L,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        ).displayed.first()

        val recoveryUi = store.select(
            nowMonoMs = now + 6_500L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2085,
            ownshipAltitudeMeters = 1_000.0,
            referenceSampleMonoMs = now + 6_500L,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        ).displayed.first()

        val deEscalatedUi = store.select(
            nowMonoMs = now + 11_000L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2075,
            ownshipAltitudeMeters = 1_000.0,
            referenceSampleMonoMs = now + 11_000L,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        ).displayed.first()

        assertEquals(AdsbProximityTier.RED, closingUi.proximityTier)
        assertTrue(closingUi.isClosing)
        assertEquals(AdsbProximityTier.RED, recoveryUi.proximityTier)
        assertFalse(recoveryUi.isClosing)
        assertEquals(AdsbProximityReason.RECOVERY_DWELL, recoveryUi.proximityReason)
        assertEquals(AdsbProximityTier.AMBER, deEscalatedUi.proximityTier)
        assertFalse(deEscalatedUi.isClosing)
    }

    @Test
    fun select_sameInputSequence_producesDeterministicTierTransitions() {
        val firstRun = runDeterministicScenario(AdsbTrafficStore())
        val secondRun = runDeterministicScenario(AdsbTrafficStore())

        assertEquals(firstRun, secondRun)
    }

}
