package com.example.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbTrafficStoreCirclingEmergencyTest {

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

}
