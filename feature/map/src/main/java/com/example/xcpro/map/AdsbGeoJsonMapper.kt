package com.example.xcpro.map

import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.ui.aircraftIcon
import com.example.xcpro.common.units.UnitsPreferences
import com.google.gson.JsonObject
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

internal object AdsbGeoJsonMapper {
    const val PROP_ICAO24 = "icao24"
    const val PROP_LABEL_TOP = "label_top"
    const val PROP_LABEL_BOTTOM = "label_bottom"
    const val PROP_ICON_ID = "icon_id"
    const val PROP_TRACK_DEG = "track_deg"
    const val PROP_ALPHA = "alpha"
    const val PROP_DISTANCE_M = "distance_m"
    const val PROP_HAS_OWNSHIP_REF = "has_ownship_ref"
    const val PROP_IS_EMERGENCY = "is_emergency"
    const val PROP_IS_CIRCLING_RULE = "is_circling_rule"
    const val PROP_IS_EMERGENCY_AUDIO_ELIGIBLE = "is_emergency_audio_eligible"
    const val PROP_PROXIMITY_TIER = "proximity_tier"
    const val PROP_PROXIMITY_REASON = "proximity_reason"
    const val PROP_ALT_M = "alt_m"
    const val PROP_SPEED_MPS = "speed_mps"
    const val PROP_VS_MPS = "vs_mps"
    const val PROP_AGE_SEC = "age_s"
    const val PROP_CATEGORY = "category"

    fun toFeature(
        target: AdsbTrafficUiModel,
        ownshipAltitudeMeters: Double?,
        unitsPreferences: UnitsPreferences,
        iconStyleIdOverride: String? = null
    ): Feature? {
        if (!target.lat.isFinite() || !target.lon.isFinite()) return null
        val feature = Feature.fromGeometry(
            Point.fromLngLat(target.lon, target.lat),
            JsonObject(),
            target.id.raw
        )
        val ownshipAltitudeForLabel = if (target.usesOwnshipReference) {
            ownshipAltitudeMeters
        } else {
            null
        }
        val labelMapping = AdsbMarkerLabelMapper.map(
            targetAltitudeMeters = target.altitudeM,
            ownshipAltitudeMeters = ownshipAltitudeForLabel,
            distanceMeters = target.distanceMeters,
            unitsPreferences = unitsPreferences
        )
        val aircraftIcon = target.aircraftIcon()
        val normalIconStyleId = iconStyleIdOverride ?: aircraftIcon.styleImageId
        feature.addStringProperty(PROP_ICAO24, target.id.raw)
        feature.addStringProperty(PROP_LABEL_TOP, labelMapping.topLabel)
        feature.addStringProperty(PROP_LABEL_BOTTOM, labelMapping.bottomLabel)
        feature.addStringProperty(
            PROP_ICON_ID,
            if (target.isEmergencyCollisionRisk) {
                "${normalIconStyleId}_emergency"
            } else {
                normalIconStyleId
            }
        )
        if (target.distanceMeters.isFinite()) {
            feature.addNumberProperty(PROP_DISTANCE_M, target.distanceMeters)
        }
        feature.addNumberProperty(PROP_HAS_OWNSHIP_REF, if (target.usesOwnshipReference) 1 else 0)
        feature.addNumberProperty(PROP_IS_EMERGENCY, if (target.isEmergencyCollisionRisk) 1 else 0)
        feature.addNumberProperty(PROP_IS_CIRCLING_RULE, if (target.isCirclingEmergencyRedRule) 1 else 0)
        feature.addNumberProperty(
            PROP_IS_EMERGENCY_AUDIO_ELIGIBLE,
            if (target.isEmergencyAudioEligible) 1 else 0
        )
        feature.addNumberProperty(PROP_PROXIMITY_TIER, target.proximityTier.code)
        feature.addStringProperty(PROP_PROXIMITY_REASON, target.proximityReason.code)
        target.trackDeg
            ?.takeIf { it.isFinite() }
            ?.let { feature.addNumberProperty(PROP_TRACK_DEG, normalizeTrackDegrees(it)) }
        target.altitudeM?.let { feature.addNumberProperty(PROP_ALT_M, it) }
        target.speedMps?.let { feature.addNumberProperty(PROP_SPEED_MPS, it) }
        target.climbMps
            ?.takeIf { it.isFinite() }
            ?.let { feature.addNumberProperty(PROP_VS_MPS, it) }
        target.category?.let { feature.addNumberProperty(PROP_CATEGORY, it) }
        feature.addNumberProperty(PROP_AGE_SEC, target.ageSec)
        return feature
    }

    private fun normalizeTrackDegrees(value: Double): Double {
        val normalized = value % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }
}
