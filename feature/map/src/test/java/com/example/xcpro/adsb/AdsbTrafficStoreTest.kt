package com.example.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbTrafficStoreTest {

    @Test
    fun purgeExpired_removesOnlyExpiredTargets() {
        val store = AdsbTrafficStore()
        val now = 200_000L
        val fresh = target(index = 1, receivedMonoMs = now - 10_000L)
        val expired = target(index = 2, receivedMonoMs = now - 130_000L)

        store.upsertAll(listOf(fresh, expired))
        val removed = store.purgeExpired(nowMonoMs = now, expiryAfterSec = 120)
        val selection = store.select(
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

        assertTrue(removed)
        assertEquals(1, selection.displayed.size)
        assertEquals(fresh.id, selection.displayed.first().id)
    }

    @Test
    fun select_appliesCapAndStaleFlag() {
        val store = AdsbTrafficStore()
        val now = 300_000L
        val targets = buildList {
            for (i in 0 until 31) {
                add(
                    target(
                        index = i + 1,
                        lat = -33.8688 + (i * 0.0006),
                        lon = 151.2093,
                        receivedMonoMs = if (i == 0) now - 70_000L else now
                    )
                )
            }
        }
        store.upsertAll(targets)

        val selection = store.select(
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

        assertEquals(31, selection.withinRadiusCount)
        assertEquals(30, selection.displayed.size)
        val uncappedSelection = store.select(
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
            maxDisplayed = 31,
            staleAfterSec = 60
        )
        assertTrue(uncappedSelection.displayed.any { it.id == targets.first().id && it.isStale })
    }

    @Test
    fun select_marksEmergencyOnlyWhenCloseInboundAndActivelyClosing() {
        val store = AdsbTrafficStore()
        val now = 400_000L
        val inboundFarther = target(
            index = 1,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(trackDeg = 270.0)
        val inboundCloser = inboundFarther.copy(
            lon = 151.2140,
            receivedMonoMs = now + 2_000L
        )
        val outbound = target(
            index = 2,
            lat = -33.8688,
            lon = 151.2140,
            receivedMonoMs = now
        ).copy(trackDeg = 90.0)
        val outboundUpdate = outbound.copy(receivedMonoMs = now + 2_000L)
        store.upsertAll(listOf(inboundFarther, outbound))

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
        store.upsertAll(listOf(inboundCloser, outboundUpdate))
        val selection = store.select(
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

        val inboundUi = selection.displayed.first { it.id == inboundCloser.id }
        val outboundUi = selection.displayed.first { it.id == outbound.id }
        assertTrue(inboundUi.isEmergencyCollisionRisk)
        assertEquals(AdsbProximityReason.GEOMETRY_EMERGENCY_APPLIED, inboundUi.proximityReason)
        assertFalse(outboundUi.isEmergencyCollisionRisk)
        assertEquals(AdsbProximityReason.DIVERGING_OR_STEADY, outboundUi.proximityReason)
    }

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
    fun select_usesReferencePositionForDistanceAndBearing() {
        val store = AdsbTrafficStore()
        val now = 500_000L
        val target = target(
            index = 3,
            lat = -33.8688,
            lon = 151.2140,
            receivedMonoMs = now
        )
        store.upsertAll(listOf(target))

        val selection = store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8600,
            referenceLon = 151.2000,
            ownshipAltitudeMeters = 1000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        val displayed = selection.displayed.first()
        assertTrue(displayed.distanceMeters > 1_000.0)
    }

    @Test
    fun select_appliesVerticalFilterWhenOwnshipAltitudeAvailable() {
        val store = AdsbTrafficStore()
        val now = 510_000L
        val low = target(index = 10, receivedMonoMs = now).copy(altitudeM = 1_050.0)
        val high = target(index = 11, receivedMonoMs = now).copy(altitudeM = 1_900.0)
        store.upsertAll(listOf(low, high))

        val selection = store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 500.0,
            verticalBelowMeters = 500.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        assertEquals(2, selection.withinRadiusCount)
        assertEquals(1, selection.withinVerticalCount)
        assertEquals(1, selection.filteredByVerticalCount)
        assertEquals(low.id, selection.displayed.first().id)
    }

    @Test
    fun select_verticalFilterFailsOpenWhenOwnshipAltitudeMissing() {
        val store = AdsbTrafficStore()
        val now = 520_000L
        val low = target(index = 20, receivedMonoMs = now).copy(altitudeM = 1_050.0)
        val high = target(index = 21, receivedMonoMs = now).copy(altitudeM = 1_900.0)
        store.upsertAll(listOf(low, high))

        val selection = store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = null,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 100.0,
            verticalBelowMeters = 100.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        assertEquals(2, selection.withinRadiusCount)
        assertEquals(2, selection.withinVerticalCount)
        assertEquals(0, selection.filteredByVerticalCount)
    }

    @Test
    fun select_disablesGeometryEmergencyWhenOwnshipAltitudeMissing() {
        val store = AdsbTrafficStore()
        val now = 525_000L
        val inboundFarther = target(
            index = 22,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(
            trackDeg = 270.0,
            altitudeM = 1_200.0
        )
        val inboundCloser = inboundFarther.copy(
            lon = 151.2140,
            receivedMonoMs = now + 2_000L
        )
        store.upsertAll(listOf(inboundFarther))

        store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = null,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        store.upsertAll(listOf(inboundCloser))
        val selection = store.select(
            nowMonoMs = now + 2_000L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = null,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        val ui = selection.displayed.first()
        assertTrue(ui.isClosing)
        assertFalse(ui.isEmergencyCollisionRisk)
        assertEquals(AdsbProximityTier.RED, ui.proximityTier)
    }

    @Test
    fun select_disablesEmergencyRiskWhenOwnshipReferenceUnavailable() {
        val store = AdsbTrafficStore()
        val now = 530_000L
        val inbound = target(
            index = 30,
            lat = -33.8688,
            lon = 151.2140,
            receivedMonoMs = now
        ).copy(trackDeg = 270.0)
        store.upsertAll(listOf(inbound))

        val selection = store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = false,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        assertFalse(selection.displayed.first().isEmergencyCollisionRisk)
        assertFalse(selection.displayed.first().usesOwnshipReference)
        assertEquals(AdsbProximityTier.NEUTRAL, selection.displayed.first().proximityTier)
        assertEquals(AdsbProximityReason.NO_OWNSHIP_REFERENCE, selection.displayed.first().proximityReason)
    }

    @Test
    fun select_usesStableTieBreakOrderingForEqualPriorityTargets() {
        val store = AdsbTrafficStore()
        val now = 540_000L
        val firstInserted = target(index = 2, receivedMonoMs = now).copy(
            lat = -33.8688,
            lon = 151.2093,
            trackDeg = null
        )
        val secondInserted = target(index = 1, receivedMonoMs = now).copy(
            lat = -33.8688,
            lon = 151.2093,
            trackDeg = null
        )
        store.upsertAll(listOf(firstInserted, secondInserted))

        repeat(5) {
            val selection = store.select(
                nowMonoMs = now,
                queryCenterLat = -33.8688,
                queryCenterLon = 151.2093,
                referenceLat = -33.8688,
                referenceLon = 151.2093,
                ownshipAltitudeMeters = 1_000.0,
                usesOwnshipReference = false,
                radiusMeters = 20_000.0,
                verticalAboveMeters = 5_000.0,
                verticalBelowMeters = 5_000.0,
                maxDisplayed = 30,
                staleAfterSec = 60
            )
            val orderedIds = selection.displayed.map { it.id.raw }
            assertEquals(listOf("000001", "000002"), orderedIds)
        }
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
        val reClosing = far.copy(
            lon = 151.2130,
            receivedMonoMs = now + 8_300L
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

        store.upsertAll(listOf(reClosing))
        val reClosingUi = selectAt(store = store, nowMonoMs = now + 8_300L).displayed.first()

        assertEquals(AdsbProximityTier.RED, closingUi.proximityTier)
        assertTrue(closingUi.isClosing)
        assertEquals(AdsbProximityTier.RED, recoveringUi.proximityTier)
        assertFalse(recoveringUi.isClosing)
        assertEquals(AdsbProximityTier.AMBER, deEscalatedUi.proximityTier)
        assertFalse(deEscalatedUi.isClosing)
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
    fun select_disablesEmergencyWhenClosingTargetAgeExceedsThreshold() {
        val store = AdsbTrafficStore()
        val now = 700_000L
        val inboundFar = target(
            index = 41,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(trackDeg = 270.0)
        val inboundFreshClose = inboundFar.copy(
            lon = 151.2140,
            receivedMonoMs = now + 2_000L
        )
        val inboundStaleClose = inboundFar.copy(
            lon = 151.2130,
            receivedMonoMs = now - 30_000L
        )

        store.upsertAll(listOf(inboundFar))
        selectAt(store = store, nowMonoMs = now)

        store.upsertAll(listOf(inboundFreshClose))
        val freshUi = selectAt(store = store, nowMonoMs = now + 2_000L).displayed.first()

        store.upsertAll(listOf(inboundStaleClose))
        val staleUi = selectAt(store = store, nowMonoMs = now + 3_000L).displayed.first()

        assertTrue(freshUi.isEmergencyCollisionRisk)
        assertEquals(AdsbProximityTier.EMERGENCY, freshUi.proximityTier)
        assertTrue(staleUi.isClosing)
        assertTrue(staleUi.ageSec > 20)
        assertFalse(staleUi.isEmergencyCollisionRisk)
        assertEquals(AdsbProximityTier.RED, staleUi.proximityTier)
    }

    @Test
    fun select_usesProviderLastContactAgeWhenOlderThanReceiveAge() {
        val store = AdsbTrafficStore()
        val nowMonoMs = 800_000L
        val nowWallEpochSec = 1_710_000_060L
        val far = target(
            index = 50,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = nowMonoMs
        ).copy(trackDeg = 270.0, lastContactEpochSec = 1_710_000_000L)
        val close = far.copy(
            lon = 151.2140,
            receivedMonoMs = nowMonoMs + 2_000L
        )

        store.upsertAll(listOf(far))
        store.select(
            nowMonoMs = nowMonoMs,
            nowWallEpochSec = nowWallEpochSec,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        store.upsertAll(listOf(close))
        val selection = store.select(
            nowMonoMs = nowMonoMs + 2_000L,
            nowWallEpochSec = nowWallEpochSec + 2,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        val ui = selection.displayed.first()
        assertTrue(ui.isClosing)
        assertTrue(ui.ageSec >= 60)
        assertFalse(ui.isEmergencyCollisionRisk)
    }

    @Test
    fun select_circlingRuleForcesRedAndEmergencyAudioWithoutGeometryEmergency() {
        val store = AdsbTrafficStore()
        val now = 900_000L
        val inboundFar = target(
            index = 60,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(trackDeg = 270.0)
        val inboundCloser = inboundFar.copy(
            lon = 151.2140,
            receivedMonoMs = now + 2_000L
        )
        store.upsertAll(listOf(inboundFar))

        store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            ownshipIsCircling = true,
            circlingFeatureEnabled = true,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        store.upsertAll(listOf(inboundCloser))
        val selection = store.select(
            nowMonoMs = now + 2_000L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            ownshipIsCircling = true,
            circlingFeatureEnabled = true,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        val ui = selection.displayed.first()
        assertEquals(AdsbProximityTier.RED, ui.proximityTier)
        assertTrue(ui.isCirclingEmergencyRedRule)
        assertTrue(ui.isEmergencyAudioEligible)
        assertFalse(ui.isEmergencyCollisionRisk)
        assertEquals(AdsbProximityReason.CIRCLING_RULE_APPLIED, ui.proximityReason)
    }

    @Test
    fun select_circlingRuleDisabledFallsBackToGeometryEmergency() {
        val store = AdsbTrafficStore()
        val now = 910_000L
        val inboundFar = target(
            index = 61,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(trackDeg = 270.0)
        val inboundCloser = inboundFar.copy(
            lon = 151.2140,
            receivedMonoMs = now + 2_000L
        )
        store.upsertAll(listOf(inboundFar))

        store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            ownshipIsCircling = true,
            circlingFeatureEnabled = false,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        store.upsertAll(listOf(inboundCloser))
        val selection = store.select(
            nowMonoMs = now + 2_000L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            ownshipIsCircling = true,
            circlingFeatureEnabled = false,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        val ui = selection.displayed.first()
        assertEquals(AdsbProximityTier.EMERGENCY, ui.proximityTier)
        assertFalse(ui.isCirclingEmergencyRedRule)
        assertTrue(ui.isEmergencyCollisionRisk)
        assertTrue(ui.isEmergencyAudioEligible)
        assertEquals(AdsbProximityReason.GEOMETRY_EMERGENCY_APPLIED, ui.proximityReason)
    }

    @Test
    fun select_circlingRuleVerticalCapFallbacksToGeometryEmergency() {
        val store = AdsbTrafficStore()
        val now = 920_000L
        val inboundFar = target(
            index = 62,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(
            trackDeg = 270.0,
            altitudeM = 1_400.0
        )
        val inboundCloser = inboundFar.copy(
            lon = 151.2140,
            receivedMonoMs = now + 2_000L
        )
        store.upsertAll(listOf(inboundFar))

        store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 1_000.0,
            verticalBelowMeters = 1_000.0,
            ownshipIsCircling = true,
            circlingFeatureEnabled = true,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        store.upsertAll(listOf(inboundCloser))
        val selection = store.select(
            nowMonoMs = now + 2_000L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 1_000.0,
            verticalBelowMeters = 1_000.0,
            ownshipIsCircling = true,
            circlingFeatureEnabled = true,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        val ui = selection.displayed.first()
        assertEquals(AdsbProximityTier.EMERGENCY, ui.proximityTier)
        assertFalse(ui.isCirclingEmergencyRedRule)
        assertTrue(ui.isEmergencyCollisionRisk)
        assertTrue(ui.isEmergencyAudioEligible)
        assertEquals(AdsbProximityReason.GEOMETRY_EMERGENCY_APPLIED, ui.proximityReason)
    }

    @Test
    fun select_reportsEmergencyAudioCandidateEvenWhenCappedOut() {
        val store = AdsbTrafficStore()
        val now = 930_000L
        val circlingFar = target(
            index = 70,
            lat = -33.8688,
            lon = 151.2210,
            receivedMonoMs = now
        ).copy(trackDeg = null, altitudeM = 1_000.0)
        val circlingClose = circlingFar.copy(
            lon = 151.2190,
            receivedMonoMs = now + 2_000L
        )
        val nearbyNonEmergency = buildList {
            for (i in 1..30) {
                add(
                    target(
                        index = 700 + i,
                        lat = -33.8688,
                        lon = 151.2093 + (i * 0.0001),
                        receivedMonoMs = now + 2_000L
                    ).copy(
                        trackDeg = null,
                        altitudeM = 1_000.0
                    )
                )
            }
        }

        store.upsertAll(listOf(circlingFar) + nearbyNonEmergency)
        store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            ownshipIsCircling = true,
            circlingFeatureEnabled = true,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        store.upsertAll(listOf(circlingClose))
        val selection = store.select(
            nowMonoMs = now + 2_000L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            ownshipIsCircling = true,
            circlingFeatureEnabled = true,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        assertEquals(31, selection.withinVerticalCount)
        assertEquals(30, selection.displayed.size)
        assertTrue(selection.displayed.none { it.id == circlingClose.id })
        assertEquals(circlingClose.id, selection.emergencyAudioCandidateId)
    }

    @Test
    fun select_emergencyCandidate_prefersGeometryEmergencyOverCirclingRule() {
        val store = AdsbTrafficStore()
        val now = 940_000L

        val geometryFar = target(
            index = 80,
            lat = -33.8688,
            lon = 151.2215,
            receivedMonoMs = now
        ).copy(
            trackDeg = 270.0,
            altitudeM = 1_400.0
        )
        val geometryClose = geometryFar.copy(
            lon = 151.2145,
            receivedMonoMs = now + 2_000L
        )

        val circlingFar = target(
            index = 81,
            lat = -33.8688,
            lon = 151.2210,
            receivedMonoMs = now
        ).copy(
            trackDeg = null,
            altitudeM = 1_000.0
        )
        val circlingClose = circlingFar.copy(
            lon = 151.2138,
            receivedMonoMs = now + 2_000L
        )

        store.upsertAll(listOf(geometryFar, circlingFar))
        store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 1_000.0,
            verticalBelowMeters = 1_000.0,
            ownshipIsCircling = true,
            circlingFeatureEnabled = true,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        store.upsertAll(listOf(geometryClose, circlingClose))
        val selection = store.select(
            nowMonoMs = now + 2_000L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 1_000.0,
            verticalBelowMeters = 1_000.0,
            ownshipIsCircling = true,
            circlingFeatureEnabled = true,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        val geometryUi = selection.displayed.first { it.id == geometryClose.id }
        val circlingUi = selection.displayed.first { it.id == circlingClose.id }
        assertTrue(geometryUi.isEmergencyCollisionRisk)
        assertTrue(circlingUi.isCirclingEmergencyRedRule)
        assertEquals(geometryClose.id, selection.emergencyAudioCandidateId)
    }

    @Test
    fun select_sameInputSequence_producesDeterministicTierTransitions() {
        val firstRun = runDeterministicScenario(AdsbTrafficStore())
        val secondRun = runDeterministicScenario(AdsbTrafficStore())

        assertEquals(firstRun, secondRun)
    }

}
