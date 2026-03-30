package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

internal fun buildOgnTrafficOverlayFeatures(
    nowMonoMs: Long,
    targets: List<OgnTrafficTarget>,
    ownshipAltitudeMeters: Double?,
    visibleBounds: LatLngBounds?,
    altitudeUnit: AltitudeUnit,
    useSatelliteContrastIcons: Boolean,
    unitsPreferences: UnitsPreferences,
    maxTargets: Int,
    staleVisualAfterMs: Long,
    liveAlpha: Double,
    staleAlpha: Double
): List<Feature> {
    val features = ArrayList<Feature>(targets.size.coerceAtMost(maxTargets))
    for (target in targets) {
        if (features.size >= maxTargets) break
        if (!isValidOgnCoordinate(target.latitude, target.longitude)) continue
        if (!isOgnInVisibleBounds(target.latitude, target.longitude, visibleBounds)) continue

        val feature = Feature.fromGeometry(
            Point.fromLngLat(target.longitude, target.latitude)
        )
        feature.addStringProperty(PROP_TARGET_KEY, target.canonicalKey)
        feature.addStringProperty(PROP_TARGET_ID, target.id)

        val icon = iconForOgnAircraftIdentity(
            aircraftTypeCode = target.identity?.aircraftTypeCode,
            competitionNumber = target.identity?.competitionNumber
        )
        val secondaryLabel = OgnIdentifierDistanceLabelMapper.map(
            competitionId = target.identity?.competitionNumber,
            registration = target.identity?.registration,
            distanceMeters = target.distanceMeters,
            unitsPreferences = unitsPreferences
        )
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            OgnRelativeAltitudeFeatureMapperInput(
                targetAltitudeMeters = target.altitudeMeters,
                ownshipAltitudeMeters = ownshipAltitudeMeters,
                altitudeUnit = altitudeUnit,
                icon = icon,
                defaultIconStyleImageId = resolveOgnStyleImageId(
                    icon = icon,
                    useSatelliteContrastIcons = useSatelliteContrastIcons
                ),
                gliderAboveIconStyleImageId = RELATIVE_GLIDER_ABOVE_ICON_IMAGE_ID,
                gliderBelowIconStyleImageId = RELATIVE_GLIDER_BELOW_ICON_IMAGE_ID,
                gliderNearIconStyleImageId = RELATIVE_GLIDER_NEAR_ICON_IMAGE_ID,
                secondaryLabelText = secondaryLabel.text
            )
        )
        feature.addStringProperty(PROP_ICON_ID, mapping.iconStyleImageId)
        feature.addStringProperty(PROP_TOP_LABEL, mapping.topLabel)
        feature.addStringProperty(PROP_BOTTOM_LABEL, mapping.bottomLabel)
        feature.addNumberProperty(
            PROP_ALPHA,
            if (target.isStale(nowMonoMs, staleVisualAfterMs)) staleAlpha else liveAlpha
        )
        if (target.trackDegrees?.isFinite() == true) {
            feature.addNumberProperty(PROP_TRACK_DEG, target.trackDegrees)
        }
        features += feature
    }
    return features
}

internal fun renderOgnTrafficFrame(
    source: GeoJsonSource,
    nowMonoMs: Long,
    targets: List<OgnTrafficTarget>,
    ownshipAltitudeMeters: Double?,
    visibleBounds: LatLngBounds?,
    altitudeUnit: AltitudeUnit,
    useSatelliteContrastIcons: Boolean,
    unitsPreferences: UnitsPreferences,
    maxTargets: Int,
    staleVisualAfterMs: Long,
    liveAlpha: Double,
    staleAlpha: Double
) {
    source.setGeoJson(
        FeatureCollection.fromFeatures(
            buildOgnTrafficOverlayFeatures(
                nowMonoMs = nowMonoMs,
                targets = targets,
                ownshipAltitudeMeters = ownshipAltitudeMeters,
                visibleBounds = visibleBounds,
                altitudeUnit = altitudeUnit,
                useSatelliteContrastIcons = useSatelliteContrastIcons,
                unitsPreferences = unitsPreferences,
                maxTargets = maxTargets,
                staleVisualAfterMs = staleVisualAfterMs,
                liveAlpha = liveAlpha,
                staleAlpha = staleAlpha
            ).toTypedArray()
        )
    )
}

internal fun resolveOgnTrafficTargetKey(feature: Feature): String? {
    val key = when {
        feature.hasProperty(PROP_TARGET_KEY) ->
            runCatching { feature.getStringProperty(PROP_TARGET_KEY) }.getOrNull()

        feature.hasProperty(PROP_TARGET_ID) ->
            runCatching { feature.getStringProperty(PROP_TARGET_ID) }.getOrNull()

        else -> null
    }
    return key?.trim()?.takeIf { it.isNotEmpty() }
}
