package com.trust3.xcpro.adsb

import com.trust3.xcpro.adsb.metadata.domain.MetadataAvailability
import com.trust3.xcpro.adsb.metadata.domain.MetadataSyncState
import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbMarkerDetailsSheetSemanticsTest {

    @Test
    fun semantics_ownshipUnavailable_showsFallbackLabels() {
        val details = sampleDetails(
            usesOwnshipReference = false,
            proximityTier = AdsbProximityTier.NEUTRAL,
            proximityReason = AdsbProximityReason.NO_OWNSHIP_REFERENCE,
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
            proximityReason = AdsbProximityReason.RECOVERY_DWELL,
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
            proximityReason = AdsbProximityReason.GEOMETRY_EMERGENCY_APPLIED,
            isClosing = true,
            closingRateMps = 2.4,
            isEmergencyCollisionRisk = true,
            isEmergencyAudioEligible = true
        )

        assertEquals("Emergency", proximityTierText(details.proximityTier))
        assertEquals("Closing", proximityTrendText(details))
        assertEquals("Emergency geometry and active closing", proximityReasonText(details))
        assertEquals("Geometry emergency rule", emergencyRuleSourceText(details))
        assertEquals("Eligible", emergencyAudioEligibilityText(details))
        assertEquals("+2.4 m/s", closingRateText(details.closingRateMps))
    }

    @Test
    fun semantics_circlingRuleReason_isExplicit() {
        val details = sampleDetails(
            usesOwnshipReference = true,
            proximityTier = AdsbProximityTier.RED,
            proximityReason = AdsbProximityReason.CIRCLING_RULE_APPLIED,
            isClosing = true,
            closingRateMps = 1.8,
            isCirclingEmergencyRedRule = true,
            isEmergencyAudioEligible = true
        )

        assertEquals(
            "Circling emergency rule applied (1 km + vertical cap + closing)",
            proximityReasonText(details)
        )
        assertEquals("Circling emergency RED rule", emergencyRuleSourceText(details))
        assertEquals("Eligible", emergencyAudioEligibilityText(details))
    }

    @Test
    fun semantics_audioIneligible_showsTrackUnavailableReason() {
        val details = sampleDetails(
            usesOwnshipReference = true,
            proximityTier = AdsbProximityTier.RED,
            proximityReason = AdsbProximityReason.DIVERGING_OR_STEADY,
            isClosing = true,
            closingRateMps = 0.1,
            isEmergencyAudioEligible = false,
            emergencyAudioIneligibilityReason =
                AdsbEmergencyAudioIneligibilityReason.TARGET_TRACK_UNAVAILABLE,
            trackDeg = null
        )

        assertEquals("Not eligible", emergencyAudioEligibilityText(details))
        assertEquals("Target track unavailable", emergencyAudioIneligibilityText(details))
    }

    @Test
    fun semantics_audioIneligible_showsNoOwnshipReason() {
        val details = sampleDetails(
            usesOwnshipReference = false,
            proximityTier = AdsbProximityTier.NEUTRAL,
            proximityReason = AdsbProximityReason.NO_OWNSHIP_REFERENCE,
            isClosing = false,
            closingRateMps = null,
            isEmergencyAudioEligible = false,
            emergencyAudioIneligibilityReason =
                AdsbEmergencyAudioIneligibilityReason.NO_OWNSHIP_REFERENCE
        )

        assertEquals("Not eligible", emergencyAudioEligibilityText(details))
        assertEquals("No ownship reference", emergencyAudioIneligibilityText(details))
    }

    private fun sampleDetails(
        usesOwnshipReference: Boolean,
        proximityTier: AdsbProximityTier,
        proximityReason: AdsbProximityReason,
        isClosing: Boolean,
        closingRateMps: Double?,
        isEmergencyCollisionRisk: Boolean = false,
        isEmergencyAudioEligible: Boolean = false,
        emergencyAudioIneligibilityReason: AdsbEmergencyAudioIneligibilityReason? = null,
        isCirclingEmergencyRedRule: Boolean = false,
        trackDeg: Double? = 180.0
    ): AdsbSelectedTargetDetails {
        val id = Icao24.from("abc123") ?: error("invalid test id")
        return AdsbSelectedTargetDetails(
            id = id,
            callsign = "TEST01",
            lat = -35.0,
            lon = 149.0,
            altitudeM = 1000.0,
            speedMps = 70.0,
            trackDeg = trackDeg,
            climbMps = 0.5,
            ageSec = 2,
            isStale = false,
            distanceMeters = 1_500.0,
            bearingDegFromUser = 220.0,
            usesOwnshipReference = usesOwnshipReference,
            proximityTier = proximityTier,
            proximityReason = proximityReason,
            isClosing = isClosing,
            closingRateMps = closingRateMps,
            isEmergencyCollisionRisk = isEmergencyCollisionRisk,
            isEmergencyAudioEligible = isEmergencyAudioEligible,
            emergencyAudioIneligibilityReason = emergencyAudioIneligibilityReason,
            isCirclingEmergencyRedRule = isCirclingEmergencyRedRule,
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
