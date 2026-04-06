package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

internal fun selectRenderableOgnTargets(
    targets: List<OgnTrafficTarget>,
    visibleBounds: LatLngBounds?,
    maxTargets: Int
): List<OgnTrafficTarget> {
    if (targets.isEmpty() || maxTargets <= 0) return emptyList()
    val renderTargets = ArrayList<OgnTrafficTarget>(targets.size.coerceAtMost(maxTargets))
    for (target in targets) {
        if (renderTargets.size >= maxTargets) break
        if (!isValidOgnCoordinate(target.latitude, target.longitude)) continue
        if (!isOgnInVisibleBounds(target.latitude, target.longitude, visibleBounds)) continue
        renderTargets += target
    }
    return renderTargets
}

internal fun buildOgnTrafficOverlayFeatures(
    nowMonoMs: Long,
    targets: List<OgnTrafficTarget>,
    fullLabelKeys: Set<String>,
    displayCoordinatesByKey: Map<String, TrafficDisplayCoordinate> = emptyMap(),
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
    val renderTargets = selectRenderableOgnTargets(
        targets = targets,
        visibleBounds = visibleBounds,
        maxTargets = maxTargets
    )
    val features = ArrayList<Feature>(renderTargets.size)
    for (target in renderTargets) {
        val displayCoordinate = displayCoordinatesByKey[target.canonicalKey]
        val renderLatitude = displayCoordinate?.latitude ?: target.latitude
        val renderLongitude = displayCoordinate?.longitude ?: target.longitude
        val feature = Feature.fromGeometry(
            Point.fromLngLat(renderLongitude, renderLatitude)
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
                distanceMeters = target.distanceMeters,
                altitudeUnit = altitudeUnit,
                icon = icon,
                defaultIconStyleImageId = resolveOgnStyleImageId(
                    icon = icon,
                    useSatelliteContrastIcons = useSatelliteContrastIcons
                ),
                gliderAboveIconStyleImageId = RELATIVE_GLIDER_ABOVE_ICON_IMAGE_ID,
                gliderBelowIconStyleImageId = RELATIVE_GLIDER_BELOW_ICON_IMAGE_ID,
                gliderNearIconStyleImageId = RELATIVE_GLIDER_NEAR_ICON_IMAGE_ID,
                gliderCloseRedIconStyleImageId = RELATIVE_GLIDER_CLOSE_RED_ICON_IMAGE_ID,
                secondaryLabelText = secondaryLabel.text,
                speedText = formatOgnGroundSpeedText(
                    groundSpeedMps = target.groundSpeedMps,
                    unitsPreferences = unitsPreferences
                )
            )
        )
        feature.addStringProperty(PROP_ICON_ID, mapping.iconStyleImageId)
        val showFullLabel = target.canonicalKey in fullLabelKeys
        feature.addStringProperty(PROP_TOP_LABEL, if (showFullLabel) mapping.topLabel else "")
        feature.addStringProperty(PROP_BOTTOM_LABEL, if (showFullLabel) mapping.bottomLabel else "")
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

internal fun buildOgnTrafficLeaderLineFeatures(
    targets: List<OgnTrafficTarget>,
    displayCoordinatesByKey: Map<String, TrafficDisplayCoordinate>,
    visibleBounds: LatLngBounds?,
    maxTargets: Int
): List<Feature> {
    if (displayCoordinatesByKey.isEmpty()) return emptyList()
    val renderTargets = selectRenderableOgnTargets(
        targets = targets,
        visibleBounds = visibleBounds,
        maxTargets = maxTargets
    )
    val features = ArrayList<Feature>(displayCoordinatesByKey.size.coerceAtMost(renderTargets.size))
    for (target in renderTargets) {
        val displayCoordinate = displayCoordinatesByKey[target.canonicalKey] ?: continue
        if (!isValidOgnCoordinate(displayCoordinate.latitude, displayCoordinate.longitude)) continue
        val lineFeature = Feature.fromGeometry(
            LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(target.longitude, target.latitude),
                    Point.fromLngLat(displayCoordinate.longitude, displayCoordinate.latitude)
                )
            )
        )
        lineFeature.addStringProperty(PROP_TARGET_KEY, target.canonicalKey)
        features += lineFeature
    }
    return features
}

internal fun renderOgnTrafficFrame(
    source: GeoJsonSource,
    leaderLineSource: GeoJsonSource,
    nowMonoMs: Long,
    targets: List<OgnTrafficTarget>,
    fullLabelKeys: Set<String>,
    displayCoordinatesByKey: Map<String, TrafficDisplayCoordinate>,
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
                fullLabelKeys = fullLabelKeys,
                displayCoordinatesByKey = displayCoordinatesByKey,
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
    leaderLineSource.setGeoJson(
        FeatureCollection.fromFeatures(
            buildOgnTrafficLeaderLineFeatures(
                targets = targets,
                displayCoordinatesByKey = displayCoordinatesByKey,
                visibleBounds = visibleBounds,
                maxTargets = maxTargets
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

private fun formatOgnGroundSpeedText(
    groundSpeedMps: Double?,
    unitsPreferences: UnitsPreferences
): String? = groundSpeedMps
    ?.takeIf { it.isFinite() && it >= 0.0 }
    ?.let { UnitsFormatter.speed(speed = SpeedMs(it), preferences = unitsPreferences).text }
