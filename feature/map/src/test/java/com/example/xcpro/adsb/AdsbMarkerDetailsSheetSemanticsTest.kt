package com.example.xcpro.adsb

import com.example.xcpro.adsb.metadata.domain.MetadataAvailability
import com.example.xcpro.adsb.metadata.domain.MetadataSyncState
import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbMarkerDetailsSheetSemanticsTest {

    @Test
    fun semantics_ownshipUnavailable_showsFallbackLabels() {
        val details = sampleDetails(
            usesOwnshipReference = false,
            proximityTier = AdsbProximityTier.NEUTRAL,
            isClosing = false,
            closingRateMps = null
        )

        assertEquals("Distance (query-center fallback)", distanceLabelForDetails(details.usesOwnshipReference))
        assertEquals("Unavailable (fallback active)", ownshipReferenceText(details.usesOwnshipReference))
        assertEquals("Unknown (no ownship reference)", proximityTrendText(details))
        assertEquals(
            "Neutral fallback while ownship reference is unavailable",
            proximityReasonText(details)
        )
    }

    @Test
    fun semantics_recoveryDwell_isExplicitWhenNotClosingButTierStillAlerting() {
        val details = sampleDetails(
            usesOwnshipReference = true,
            proximityTier = AdsbProximityTier.AMBER,
            isClosing = false,
            closingRateMps = -0.6
        )

        assertEquals("Amber", proximityTierText(details.proximityTier))
        assertEquals("Recovery dwell active", proximityTrendText(details))
        assertEquals("Holding alert during recovery dwell", proximityReasonText(details))
        assertEquals("-0.6 m/s", closingRateText(details.closingRateMps))
    }

    @Test
    fun semantics_emergencyPriority_isExplicit() {
        val details = sampleDetails(
            usesOwnshipReference = true,
            proximityTier = AdsbProximityTier.EMERGENCY,
            isClosing = true,
            closingRateMps = 2.4,
            isEmergencyCollisionRisk = true
        )

        assertEquals("Emergency", proximityTierText(details.proximityTier))
        assertEquals("Closing", proximityTrendText(details))
        assertEquals("Emergency geometry and active closing", proximityReasonText(details))
        assertEquals("+2.4 m/s", closingRateText(details.closingRateMps))
    }

    private fun sampleDetails(
        usesOwnshipReference: Boolean,
        proximityTier: AdsbProximityTier,
        isClosing: Boolean,
        closingRateMps: Double?,
        isEmergencyCollisionRisk: Boolean = false
    ): AdsbSelectedTargetDetails {
        val id = Icao24.from("abc123") ?: error("invalid test id")
        return AdsbSelectedTargetDetails(
            id = id,
            callsign = "TEST01",
            lat = -35.0,
            lon = 149.0,
            altitudeM = 1000.0,
            speedMps = 70.0,
            trackDeg = 180.0,
            climbMps = 0.5,
            ageSec = 2,
            isStale = false,
            distanceMeters = 1_500.0,
            bearingDegFromUser = 220.0,
            usesOwnshipReference = usesOwnshipReference,
            proximityTier = proximityTier,
            isClosing = isClosing,
            closingRateMps = closingRateMps,
            isEmergencyCollisionRisk = isEmergencyCollisionRisk,
            positionSource = 0,
            category = 3,
            lastContactEpochSec = null,
            registration = null,
            typecode = null,
            model = null,
            manufacturerName = null,
            owner = null,
            operator = null,
            operatorCallsign = null,
            icaoAircraftType = null,
            metadataAvailability = MetadataAvailability.Missing,
            metadataSyncState = MetadataSyncState.Idle
        )
    }
}
