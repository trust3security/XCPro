package com.example.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbTrafficStoreEmergencyGeometryTest {

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
            ownshipTrackDeg = 90.0,
            ownshipSpeedMps = 20.0,
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
            ownshipTrackDeg = 90.0,
            ownshipSpeedMps = 20.0,
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
    fun select_disablesEmergencyWhenOwnshipTurningProjectsLargeCpaMissDistance() {
        val store = AdsbTrafficStore()
        val now = 405_000L
        val inboundFarther = target(
            index = 3,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(trackDeg = 270.0, speedMps = 35.0)
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
            ownshipAltitudeMeters = 1000.0,
            ownshipTrackDeg = 0.0,
            ownshipSpeedMps = 45.0,
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
            ownshipAltitudeMeters = 1000.0,
            ownshipTrackDeg = 0.0,
            ownshipSpeedMps = 45.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        val inboundUi = selection.displayed.first()
        assertTrue(inboundUi.isClosing)
        assertFalse(inboundUi.isEmergencyCollisionRisk)
        assertEquals(AdsbProximityTier.RED, inboundUi.proximityTier)
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
        val inboundStaleClose = inboundFreshClose.copy(
            lon = 151.2130,
            receivedMonoMs = now + 3_000L,
            contactAgeAtReceiptSec = 30
        )

        store.upsertAll(listOf(inboundFar))
        selectAt(
            store = store,
            nowMonoMs = now,
            ownshipTrackDeg = 90.0,
            ownshipSpeedMps = 20.0
        )

        store.upsertAll(listOf(inboundFreshClose))
        val freshUi = selectAt(
            store = store,
            nowMonoMs = now + 2_000L,
            ownshipTrackDeg = 90.0,
            ownshipSpeedMps = 20.0
        ).displayed.first()

        store.upsertAll(listOf(inboundStaleClose))
        val staleUi = selectAt(
            store = store,
            nowMonoMs = now + 3_000L,
            ownshipTrackDeg = 90.0,
            ownshipSpeedMps = 20.0
        ).displayed.first()

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
    fun select_forcesGreenWhenVerticalSeparationIsLargeEvenAtCloseRange() {
        val store = AdsbTrafficStore()
        val now = 810_000L
        val closeHigh = target(
            index = 60,
            lat = -33.8688,
            lon = 151.2140,
            receivedMonoMs = now
        ).copy(
            trackDeg = 270.0,
            altitudeM = 2_900.0
        )
        store.upsertAll(listOf(closeHigh))

        val selection = store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 3_000.0,
            verticalBelowMeters = 3_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        val ui = selection.displayed.first()
        assertEquals(AdsbProximityTier.GREEN, ui.proximityTier)
        assertEquals(AdsbProximityReason.DIVERGING_OR_STEADY, ui.proximityReason)
        assertFalse(ui.isEmergencyCollisionRisk)
        assertFalse(ui.isEmergencyAudioEligible)
        assertFalse(ui.isClosing)
    }

    @Test
    fun select_holdsEmergencyAcrossSingleSafeFreshSample_thenClearsOnSecond() {
        val store = AdsbTrafficStore()
        val now = 820_000L
        val far = target(
            index = 61,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(trackDeg = 270.0, speedMps = 35.0)
        val closeEmergency = far.copy(
            lon = 151.2140,
            receivedMonoMs = now + 2_000L
        )
        val safeSampleOne = closeEmergency.copy(
            lon = 151.2138,
            receivedMonoMs = now + 3_200L,
            trackDeg = 180.0
        )
        val safeSampleTwo = safeSampleOne.copy(
            lon = 151.2136,
            receivedMonoMs = now + 4_400L
        )

        store.upsertAll(listOf(far))
        store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            ownshipTrackDeg = 90.0,
            ownshipSpeedMps = 20.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        store.upsertAll(listOf(closeEmergency))
        val emergencyUi = store.select(
            nowMonoMs = now + 2_000L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            ownshipTrackDeg = 90.0,
            ownshipSpeedMps = 20.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        ).displayed.first()

        store.upsertAll(listOf(safeSampleOne))
        val heldUi = store.select(
            nowMonoMs = now + 3_200L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            ownshipTrackDeg = 90.0,
            ownshipSpeedMps = 20.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        ).displayed.first()

        store.upsertAll(listOf(safeSampleTwo))
        val clearedUi = store.select(
            nowMonoMs = now + 4_400L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            ownshipTrackDeg = 90.0,
            ownshipSpeedMps = 20.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        ).displayed.first()

        assertTrue(emergencyUi.isEmergencyCollisionRisk)
        assertEquals(AdsbProximityTier.EMERGENCY, emergencyUi.proximityTier)
        assertTrue(heldUi.isEmergencyCollisionRisk)
        assertEquals(AdsbProximityTier.EMERGENCY, heldUi.proximityTier)
        assertFalse(clearedUi.isEmergencyCollisionRisk)
        assertEquals(AdsbProximityTier.RED, clearedUi.proximityTier)
    }

    @Test
    fun select_usesDerivedTrackWhenProviderTrackIsMissing() {
        val store = AdsbTrafficStore()
        val now = 830_000L
        val far = target(
            index = 62,
            lat = -33.8688,
            lon = 151.2200,
            receivedMonoMs = now
        ).copy(trackDeg = null, speedMps = 35.0)
        val close = far.copy(
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
            ownshipTrackDeg = 90.0,
            ownshipSpeedMps = 20.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        store.upsertAll(listOf(close))
        val ui = store.select(
            nowMonoMs = now + 2_000L,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            ownshipTrackDeg = 90.0,
            ownshipSpeedMps = 20.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        ).displayed.first()

        assertTrue(ui.trackDeg != null)
        assertTrue(ui.isEmergencyCollisionRisk)
        assertEquals(AdsbProximityTier.EMERGENCY, ui.proximityTier)
        assertEquals(AdsbProximityReason.GEOMETRY_EMERGENCY_APPLIED, ui.proximityReason)
        assertTrue(ui.emergencyAudioIneligibilityReason == null)
    }

}
