package com.example.xcpro.map

import com.example.xcpro.map.AdsbProximityTier
import com.example.xcpro.map.AdsbProximityReason
import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.Icao24
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.adsb.AdsbPositionFreshnessSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbGeoJsonMapperTest {

    @Test
    fun toFeature_includesIconIdAndTrackWhenTrackPresent() {
        val target = sampleTarget(category = 10, trackDeg = 273.5)

        val feature = AdsbGeoJsonMapper.toFeature(
            target = target,
            ownshipAltitudeMeters = 900.0,
            unitsPreferences = UnitsPreferences()
        )

        assertNotNull(feature)
        feature ?: return
        assertEquals("abc123", feature.id())
        assertEquals(
            "adsb_icon_balloon",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_ICON_ID)
        )
        assertTrue(feature.hasProperty(AdsbGeoJsonMapper.PROP_TRACK_DEG))
        assertTrue(feature.hasProperty(AdsbGeoJsonMapper.PROP_DISTANCE_M))
        assertEquals(
            1.0,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_HAS_OWNSHIP_REF).toDouble(),
            1e-6
        )
        assertEquals(
            0.0,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_IS_EMERGENCY).toDouble(),
            1e-6
        )
        assertEquals(
            0.0,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_IS_CIRCLING_RULE).toDouble(),
            1e-6
        )
        assertEquals(
            0.0,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_IS_EMERGENCY_AUDIO_ELIGIBLE).toDouble(),
            1e-6
        )
        assertEquals(
            AdsbProximityTier.AMBER.code.toDouble(),
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_PROXIMITY_TIER).toDouble(),
            1e-6
        )
        assertEquals(
            "approach_closing",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_PROXIMITY_REASON)
        )
        assertEquals(
            273.5,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_TRACK_DEG).toDouble(),
            1e-6
        )
        assertEquals(
            "+100 m",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_LABEL_TOP)
        )
        assertEquals(
            "1.5 km",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_LABEL_BOTTOM)
        )
    }

    @Test
    fun toFeature_omitsTrackWhenTrackMissing() {
        val target = sampleTarget(category = 14, trackDeg = null)

        val feature = AdsbGeoJsonMapper.toFeature(
            target = target,
            ownshipAltitudeMeters = 1100.0,
            unitsPreferences = UnitsPreferences()
        )

        assertNotNull(feature)
        feature ?: return
        assertEquals(
            "adsb_icon_drone",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_ICON_ID)
        )
        assertFalse(feature.hasProperty(AdsbGeoJsonMapper.PROP_TRACK_DEG))
        assertEquals(
            "1.5 km",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_LABEL_TOP)
        )
        assertEquals(
            "-100 m",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_LABEL_BOTTOM)
        )
    }

    @Test
    fun toFeature_usesEmergencyIconWhenCollisionRiskIsTrue() {
        val target = sampleTarget(category = 9, trackDeg = 180.0, isEmergencyCollisionRisk = true)

        val feature = AdsbGeoJsonMapper.toFeature(
            target = target,
            ownshipAltitudeMeters = 900.0,
            unitsPreferences = UnitsPreferences()
        )

        assertNotNull(feature)
        feature ?: return
        assertEquals(
            "adsb_icon_glider_emergency",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_ICON_ID)
        )
        assertEquals(
            1.0,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_IS_EMERGENCY).toDouble(),
            1e-6
        )
        assertEquals(
            AdsbProximityTier.EMERGENCY.code.toDouble(),
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_PROXIMITY_TIER).toDouble(),
            1e-6
        )
        assertEquals(
            "geometry_emergency_applied",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_PROXIMITY_REASON)
        )
    }

    @Test
    fun toFeature_includesCirclingRuleAndAudioEligibilityFlags() {
        val target = sampleTarget(
            category = 1,
            trackDeg = 180.0,
            isCirclingEmergencyRedRule = true,
            isEmergencyAudioEligible = true
        )

        val feature = AdsbGeoJsonMapper.toFeature(
            target = target,
            ownshipAltitudeMeters = 900.0,
            unitsPreferences = UnitsPreferences()
        )

        assertNotNull(feature)
        feature ?: return
        assertEquals(
            1.0,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_IS_CIRCLING_RULE).toDouble(),
            1e-6
        )
        assertEquals(
            1.0,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_IS_EMERGENCY_AUDIO_ELIGIBLE).toDouble(),
            1e-6
        )
        assertEquals(
            "circling_rule_applied",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_PROXIMITY_REASON)
        )
    }

    @Test
    fun toFeature_unknownClassification_keepsUnknownSemanticIconId() {
        val target = sampleTarget(
            category = 0,
            trackDeg = 180.0
        ).copy(
            metadataTypecode = null,
            metadataIcaoAircraftType = null
        )

        val feature = AdsbGeoJsonMapper.toFeature(
            target = target,
            ownshipAltitudeMeters = 900.0,
            unitsPreferences = UnitsPreferences()
        )

        assertNotNull(feature)
        feature ?: return
        assertEquals(
            "adsb_icon_unknown",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_ICON_ID)
        )
    }

    @Test
    fun toFeature_iconOverride_appliesOverrideAndEmergencySuffix() {
        val target = sampleTarget(
            category = 0,
            trackDeg = 180.0,
            isEmergencyCollisionRisk = true
        ).copy(
            metadataTypecode = null,
            metadataIcaoAircraftType = null
        )

        val feature = AdsbGeoJsonMapper.toFeature(
            target = target,
            ownshipAltitudeMeters = 900.0,
            unitsPreferences = UnitsPreferences(),
            iconStyleIdOverride = "adsb_icon_jet_twin"
        )

        assertNotNull(feature)
        feature ?: return
        assertEquals(
            "adsb_icon_jet_twin_emergency",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_ICON_ID)
        )
    }

    @Test
    fun toFeature_marksOwnshipReferenceAvailabilityFlag() {
        val target = sampleTarget(category = 2, trackDeg = 95.0, usesOwnshipReference = false)

        val feature = AdsbGeoJsonMapper.toFeature(
            target = target,
            ownshipAltitudeMeters = 900.0,
            unitsPreferences = UnitsPreferences()
        )

        assertNotNull(feature)
        feature ?: return
        assertEquals(
            0.0,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_HAS_OWNSHIP_REF).toDouble(),
            1e-6
        )
        assertEquals(
            AdsbMarkerLabelMapper.UNKNOWN_TEXT,
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_LABEL_TOP)
        )
        assertEquals(
            "1.5 km",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_LABEL_BOTTOM)
        )
    }

    @Test
    fun toFeature_omitsDistanceWhenDistanceIsNotFinite() {
        val target = sampleTarget(
            category = 3,
            trackDeg = 210.0,
            distanceMeters = Double.NaN
        )

        val feature = AdsbGeoJsonMapper.toFeature(
            target = target,
            ownshipAltitudeMeters = 900.0,
            unitsPreferences = UnitsPreferences()
        )

        assertNotNull(feature)
        feature ?: return
        assertFalse(feature.hasProperty(AdsbGeoJsonMapper.PROP_DISTANCE_M))
        assertEquals(
            "+100 m",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_LABEL_TOP)
        )
        assertEquals(
            AdsbMarkerLabelMapper.UNKNOWN_TEXT,
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_LABEL_BOTTOM)
        )
    }

    @Test
    fun toFeature_includesFreshnessAliasAndPositionContactAges() {
        val target = sampleTarget(category = 2, trackDeg = 200.0, usesOwnshipReference = true).copy(
            positionAgeSec = 7,
            contactAgeSec = 2,
            isPositionStale = true,
            ageSec = 7,
            isStale = true,
            positionTimestampEpochSec = 1_710_000_020L,
            effectivePositionEpochSec = 1_710_000_020L
        )

        val feature = AdsbGeoJsonMapper.toFeature(
            target = target,
            ownshipAltitudeMeters = 900.0,
            unitsPreferences = UnitsPreferences()
        )

        assertNotNull(feature)
        feature ?: return
        assertEquals(7.0, feature.getNumberProperty(AdsbGeoJsonMapper.PROP_POSITION_AGE_SEC).toDouble(), 1e-6)
        assertEquals(2.0, feature.getNumberProperty(AdsbGeoJsonMapper.PROP_CONTACT_AGE_SEC).toDouble(), 1e-6)
        assertEquals(1.0, feature.getNumberProperty(AdsbGeoJsonMapper.PROP_POSITION_IS_STALE).toDouble(), 1e-6)
        assertEquals("POSITION_TIME", feature.getStringProperty(AdsbGeoJsonMapper.PROP_POSITION_FRESHNESS_SOURCE))
        assertEquals(7.0, feature.getNumberProperty(AdsbGeoJsonMapper.PROP_AGE_SEC).toDouble(), 1e-6)
    }

    private fun sampleTarget(
        category: Int?,
        trackDeg: Double?,
        isEmergencyCollisionRisk: Boolean = false,
        isCirclingEmergencyRedRule: Boolean = false,
        isEmergencyAudioEligible: Boolean = false,
        usesOwnshipReference: Boolean = true,
        distanceMeters: Double = 1500.0
    ): AdsbTrafficUiModel {
        val id = Icao24.from("abc123") ?: error("invalid test id")
        val tier = when {
            !usesOwnshipReference -> AdsbProximityTier.NEUTRAL
            isEmergencyCollisionRisk -> AdsbProximityTier.EMERGENCY
            isCirclingEmergencyRedRule -> AdsbProximityTier.RED
            else -> AdsbProximityTier.AMBER
        }
        val reason = when {
            !usesOwnshipReference -> AdsbProximityReason.NO_OWNSHIP_REFERENCE
            isEmergencyCollisionRisk -> AdsbProximityReason.GEOMETRY_EMERGENCY_APPLIED
            isCirclingEmergencyRedRule -> AdsbProximityReason.CIRCLING_RULE_APPLIED
            else -> AdsbProximityReason.APPROACH_CLOSING
        }
        return AdsbTrafficUiModel(
            id = id,
            callsign = "TEST01",
            lat = -35.0,
            lon = 149.0,
            altitudeM = 1000.0,
            speedMps = 70.0,
            trackDeg = trackDeg,
            climbMps = 0.5,
            ageSec = 3,
            isStale = false,
            distanceMeters = distanceMeters,
            bearingDegFromUser = 220.0,
            usesOwnshipReference = usesOwnshipReference,
            positionSource = 0,
            category = category,
            lastContactEpochSec = null,
            proximityTier = tier,
            proximityReason = reason,
            isEmergencyCollisionRisk = isEmergencyCollisionRisk,
            isEmergencyAudioEligible = isEmergencyAudioEligible,
            isCirclingEmergencyRedRule = isCirclingEmergencyRedRule,
            isClosing = usesOwnshipReference,
            positionAgeSec = 5,
            contactAgeSec = 1,
            isPositionStale = false,
            positionFreshnessSource = if (usesOwnshipReference) {
                AdsbPositionFreshnessSource.POSITION_TIME
            } else {
                AdsbPositionFreshnessSource.RECEIVED_MONO_FALLBACK
            }
        )
    }
}
