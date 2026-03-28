package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

internal fun buildOgnTrafficOverlayFeatures(
    nowMonoMs: Long,
    renderItems: List<OgnTrafficRenderItem>,
    ownshipAltitudeMeters: Double?,
    altitudeUnit: AltitudeUnit,
    useSatelliteContrastIcons: Boolean,
    unitsPreferences: UnitsPreferences,
    staleVisualAfterMs: Long,
    liveAlpha: Double,
    staleAlpha: Double
): List<Feature> {
    val features = ArrayList<Feature>(renderItems.size)
    for (renderItem in renderItems) {
        val feature = when (renderItem) {
            is OgnTrafficRenderItem.Single -> buildSingleOgnTrafficFeature(
                target = renderItem.target,
                nowMonoMs = nowMonoMs,
                ownshipAltitudeMeters = ownshipAltitudeMeters,
                altitudeUnit = altitudeUnit,
                useSatelliteContrastIcons = useSatelliteContrastIcons,
                unitsPreferences = unitsPreferences,
                staleVisualAfterMs = staleVisualAfterMs,
                liveAlpha = liveAlpha,
                staleAlpha = staleAlpha
            )

            is OgnTrafficRenderItem.Cluster -> buildClusterOgnTrafficFeature(
                cluster = renderItem,
                nowMonoMs = nowMonoMs,
                useSatelliteContrastIcons = useSatelliteContrastIcons,
                staleVisualAfterMs = staleVisualAfterMs,
                liveAlpha = liveAlpha,
                staleAlpha = staleAlpha
            )
        }
        if (feature != null) {
            features += feature
        }
    }
    return features
}

internal fun renderOgnTrafficFrame(
    source: GeoJsonSource,
    nowMonoMs: Long,
    renderItems: List<OgnTrafficRenderItem>,
    ownshipAltitudeMeters: Double?,
    altitudeUnit: AltitudeUnit,
    useSatelliteContrastIcons: Boolean,
    unitsPreferences: UnitsPreferences,
    staleVisualAfterMs: Long,
    liveAlpha: Double,
    staleAlpha: Double
) {
    source.setGeoJson(
        FeatureCollection.fromFeatures(
            buildOgnTrafficOverlayFeatures(
                nowMonoMs = nowMonoMs,
                renderItems = renderItems,
                ownshipAltitudeMeters = ownshipAltitudeMeters,
                altitudeUnit = altitudeUnit,
                useSatelliteContrastIcons = useSatelliteContrastIcons,
                unitsPreferences = unitsPreferences,
                staleVisualAfterMs = staleVisualAfterMs,
                liveAlpha = liveAlpha,
                staleAlpha = staleAlpha
            ).toTypedArray()
        )
    )
}

internal fun resolveOgnTrafficHitResult(feature: Feature): OgnTrafficHitResult? {
    val clusterKey = runCatching { feature.getStringProperty(PROP_CLUSTER_KEY) }.getOrNull()
        ?.trim()
        .orEmpty()
    if (clusterKey.isNotEmpty()) {
        val clusterCount = runCatching { feature.getNumberProperty(PROP_CLUSTER_COUNT).toInt() }
            .getOrNull()
            ?: return null
        val clusterLat = runCatching { feature.getNumberProperty(PROP_CLUSTER_LAT).toDouble() }
            .getOrNull()
            ?: return null
        val clusterLon = runCatching { feature.getNumberProperty(PROP_CLUSTER_LON).toDouble() }
            .getOrNull()
            ?: return null
        return OgnTrafficHitResult.Cluster(
            clusterKey = clusterKey,
            centerLatitude = clusterLat,
            centerLongitude = clusterLon,
            memberCount = clusterCount
        )
    }

    val targetKey = when {
        feature.hasProperty(PROP_TARGET_KEY) ->
            runCatching { feature.getStringProperty(PROP_TARGET_KEY) }.getOrNull()
        feature.hasProperty(PROP_TARGET_ID) ->
            runCatching { feature.getStringProperty(PROP_TARGET_ID) }.getOrNull()
        else -> null
    }?.trim().orEmpty()
    if (targetKey.isEmpty()) return null
    return OgnTrafficHitResult.Target(targetKey = targetKey)
}

private fun buildSingleOgnTrafficFeature(
    target: OgnTrafficTarget,
    nowMonoMs: Long,
    ownshipAltitudeMeters: Double?,
    altitudeUnit: AltitudeUnit,
    useSatelliteContrastIcons: Boolean,
    unitsPreferences: UnitsPreferences,
    staleVisualAfterMs: Long,
    liveAlpha: Double,
    staleAlpha: Double
): Feature? {
    if (!isValidOgnCoordinate(target.latitude, target.longitude)) return null

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
    feature.addStringProperty(PROP_CLUSTER_COUNT_LABEL, "")
    feature.addNumberProperty(
        PROP_ALPHA,
        if (target.isStale(nowMonoMs, staleVisualAfterMs)) staleAlpha else liveAlpha
    )
    if (target.trackDegrees?.isFinite() == true) {
        feature.addNumberProperty(PROP_TRACK_DEG, target.trackDegrees)
    }
    return feature
}

private fun buildClusterOgnTrafficFeature(
    cluster: OgnTrafficRenderItem.Cluster,
    nowMonoMs: Long,
    useSatelliteContrastIcons: Boolean,
    staleVisualAfterMs: Long,
    liveAlpha: Double,
    staleAlpha: Double
): Feature? {
    if (!isValidOgnCoordinate(cluster.centerLatitude, cluster.centerLongitude)) return null

    val representative = resolveClusterRepresentative(cluster.members)
    val representativeIcon = resolveOgnStyleImageId(
        icon = iconForOgnAircraftIdentity(
            aircraftTypeCode = representative.identity?.aircraftTypeCode,
            competitionNumber = representative.identity?.competitionNumber
        ),
        useSatelliteContrastIcons = useSatelliteContrastIcons
    )

    val feature = Feature.fromGeometry(
        Point.fromLngLat(cluster.centerLongitude, cluster.centerLatitude)
    )
    feature.addStringProperty(PROP_ICON_ID, representativeIcon)
    feature.addStringProperty(PROP_TOP_LABEL, "")
    feature.addStringProperty(PROP_BOTTOM_LABEL, "")
    feature.addStringProperty(PROP_CLUSTER_COUNT_LABEL, cluster.memberCount.toString())
    feature.addStringProperty(PROP_CLUSTER_KEY, cluster.clusterKey)
    feature.addNumberProperty(PROP_CLUSTER_COUNT, cluster.memberCount)
    feature.addNumberProperty(PROP_CLUSTER_LAT, cluster.centerLatitude)
    feature.addNumberProperty(PROP_CLUSTER_LON, cluster.centerLongitude)
    feature.addNumberProperty(
        PROP_ALPHA,
        if (cluster.members.all { it.isStale(nowMonoMs, staleVisualAfterMs) }) staleAlpha else liveAlpha
    )
    return feature
}

private fun resolveClusterRepresentative(members: List<OgnTrafficTarget>): OgnTrafficTarget =
    members.minWithOrNull(
        compareBy<OgnTrafficTarget> { it.distanceMeters ?: Double.MAX_VALUE }
            .thenByDescending { it.lastSeenMillis }
            .thenBy { it.canonicalKey }
    ) ?: members.first()
