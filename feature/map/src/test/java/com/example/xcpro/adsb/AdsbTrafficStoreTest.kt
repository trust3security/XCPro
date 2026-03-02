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
            receivedMonoMs = now + 2_000L
        ).copy(trackDeg = 90.0)
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
        store.upsertAll(listOf(inboundCloser, outbound))
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
        assertFalse(outboundUi.isEmergencyCollisionRisk)
    }

    @Test
    fun select_deEscalatesToGreenWhenNoLongerClosingAfterRecoveryDwell() {
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
        assertEquals(AdsbProximityTier.RED, recoveringUi.proximityTier)
        assertEquals(AdsbProximityTier.GREEN, deEscalatedUi.proximityTier)
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
    fun select_reEntersAlertTierWhenClosingResumesAfterDeEscalation() {
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
        val deEscalatedUi = selectAt(store = store, nowMonoMs = now + 7_200L).displayed.first()

        store.upsertAll(listOf(reClosing))
        val reClosingUi = selectAt(store = store, nowMonoMs = now + 8_300L).displayed.first()

        assertEquals(AdsbProximityTier.RED, closingUi.proximityTier)
        assertTrue(closingUi.isClosing)
        assertEquals(AdsbProximityTier.RED, recoveringUi.proximityTier)
        assertFalse(recoveringUi.isClosing)
        assertEquals(AdsbProximityTier.GREEN, deEscalatedUi.proximityTier)
        assertFalse(deEscalatedUi.isClosing)
        assertEquals(AdsbProximityTier.RED, reClosingUi.proximityTier)
        assertTrue(reClosingUi.isClosing)
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
    fun select_sameInputSequence_producesDeterministicTierTransitions() {
        val firstRun = runDeterministicScenario(AdsbTrafficStore())
        val secondRun = runDeterministicScenario(AdsbTrafficStore())

        assertEquals(firstRun, secondRun)
    }

    private fun runDeterministicScenario(store: AdsbTrafficStore): List<TransitionSnapshot> {
        val steps = listOf(
            ScenarioStep(nowMonoMs = 800_000L, lon = 151.2200, receivedMonoMs = 800_000L, usesOwnshipReference = true),
            ScenarioStep(nowMonoMs = 802_000L, lon = 151.2140, receivedMonoMs = 802_000L, usesOwnshipReference = true),
            ScenarioStep(nowMonoMs = 803_000L, lon = 151.2145, receivedMonoMs = 803_000L, usesOwnshipReference = true),
            ScenarioStep(nowMonoMs = 807_200L, lon = 151.2145, receivedMonoMs = 803_000L, usesOwnshipReference = true),
            ScenarioStep(nowMonoMs = 808_300L, lon = 151.2130, receivedMonoMs = 808_300L, usesOwnshipReference = true),
            ScenarioStep(nowMonoMs = 809_400L, lon = 151.2130, receivedMonoMs = 809_400L, usesOwnshipReference = false),
            ScenarioStep(nowMonoMs = 810_500L, lon = 151.2120, receivedMonoMs = 810_500L, usesOwnshipReference = true)
        )
        val prototype = target(
            index = 42,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = steps.first().receivedMonoMs
        ).copy(trackDeg = null)

        return steps.map { step ->
            store.upsertAll(
                listOf(
                    prototype.copy(
                        lon = step.lon,
                        receivedMonoMs = step.receivedMonoMs
                    )
                )
            )
            val ui = selectAt(
                store = store,
                nowMonoMs = step.nowMonoMs,
                usesOwnshipReference = step.usesOwnshipReference
            ).displayed.first()
            TransitionSnapshot(
                proximityTier = ui.proximityTier,
                isClosing = ui.isClosing,
                isEmergencyCollisionRisk = ui.isEmergencyCollisionRisk,
                usesOwnshipReference = ui.usesOwnshipReference
            )
        }
    }

    private fun selectAt(
        store: AdsbTrafficStore,
        nowMonoMs: Long,
        usesOwnshipReference: Boolean = true,
        ownshipAltitudeMeters: Double? = 1_000.0
    ): AdsbStoreSelection = store.select(
        nowMonoMs = nowMonoMs,
        queryCenterLat = -33.8688,
        queryCenterLon = 151.2093,
        referenceLat = -33.8688,
        referenceLon = 151.2093,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        usesOwnshipReference = usesOwnshipReference,
        radiusMeters = 20_000.0,
        verticalAboveMeters = 5_000.0,
        verticalBelowMeters = 5_000.0,
        maxDisplayed = 30,
        staleAfterSec = 60
    )

    private data class ScenarioStep(
        val nowMonoMs: Long,
        val lon: Double,
        val receivedMonoMs: Long,
        val usesOwnshipReference: Boolean
    )

    private data class TransitionSnapshot(
        val proximityTier: AdsbProximityTier,
        val isClosing: Boolean,
        val isEmergencyCollisionRisk: Boolean,
        val usesOwnshipReference: Boolean
    )

    private fun target(
        index: Int,
        lat: Double = -33.8688,
        lon: Double = 151.2093,
        receivedMonoMs: Long
    ): AdsbTarget {
        val id = Icao24.from("%06x".format(index)) ?: error("invalid test id")
        return AdsbTarget(
            id = id,
            callsign = "T$index",
            lat = lat,
            lon = lon,
            altitudeM = 1200.0,
            speedMps = 30.0,
            trackDeg = 180.0,
            climbMps = 0.5,
            positionSource = 0,
            category = 2,
            lastContactEpochSec = 1_710_000_000L,
            receivedMonoMs = receivedMonoMs
        )
    }
}
