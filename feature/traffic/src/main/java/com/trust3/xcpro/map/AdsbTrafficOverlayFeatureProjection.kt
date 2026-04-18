package com.trust3.xcpro.map


import com.trust3.xcpro.common.units.UnitsPreferences
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

internal fun selectRenderableAdsbTargets(
    targets: List<AdsbTrafficUiModel>,
    maxTargets: Int
): List<AdsbTrafficUiModel> {
    if (targets.isEmpty() || maxTargets <= 0) return emptyList()
    val renderTargets = ArrayList<AdsbTrafficUiModel>(targets.size.coerceAtMost(maxTargets))
    for (target in targets) {
        if (renderTargets.size >= maxTargets) break
        if (!target.lat.isFinite() || !target.lon.isFinite()) continue
        renderTargets += target
    }
    return renderTargets
}

internal fun buildAdsbTrafficOverlayFeatures(
    nowMonoMs: Long,
    targets: List<AdsbTrafficUiModel>,
    fullLabelKeys: Set<String>,
    displayCoordinatesByKey: Map<String, TrafficDisplayCoordinate> = emptyMap(),
    ownshipAltitudeMeters: Double?,
    unitsPreferences: UnitsPreferences,
    iconStyleIdOverrides: Map<String, String>,
    emergencyFlashEnabled: Boolean,
    maxTargets: Int,
    liveAlpha: Double,
    staleAlpha: Double
): Array<Feature> {
    val renderTargets = selectRenderableAdsbTargets(targets = targets, maxTargets = maxTargets)
    val features = ArrayList<Feature>(renderTargets.size)
    for (target in renderTargets) {
        val feature = AdsbGeoJsonMapper.toFeatureInternal(
            target = target,
            ownshipAltitudeMeters = ownshipAltitudeMeters,
            unitsPreferences = unitsPreferences,
            iconStyleIdOverride = iconStyleIdOverrides[target.id.raw],
            displayCoordinate = displayCoordinatesByKey[target.id.raw],
            showFullLabel = target.id.raw in fullLabelKeys
        ) ?: continue
        feature.addNumberProperty(
            AdsbGeoJsonMapper.PROP_ALPHA,
            AdsbEmergencyFlashPolicy.alphaForTarget(
                target = target,
                nowMonoMs = nowMonoMs,
                liveAlpha = liveAlpha,
                staleAlpha = staleAlpha,
                emergencyFlashEnabled = emergencyFlashEnabled
            )
        )
        features.add(feature)
    }
    return features.toTypedArray()
}

internal fun buildAdsbTrafficLeaderLineFeatures(
    targets: List<AdsbTrafficUiModel>,
    displayCoordinatesByKey: Map<String, TrafficDisplayCoordinate>,
    maxTargets: Int
): Array<Feature> {
    if (displayCoordinatesByKey.isEmpty()) return emptyArray()
    val renderTargets = selectRenderableAdsbTargets(targets = targets, maxTargets = maxTargets)
    val features = ArrayList<Feature>(displayCoordinatesByKey.size.coerceAtMost(renderTargets.size))
    for (target in renderTargets) {
        val displayCoordinate = displayCoordinatesByKey[target.id.raw] ?: continue
        if (!displayCoordinate.latitude.isFinite() || !displayCoordinate.longitude.isFinite()) continue
        val feature = Feature.fromGeometry(
            LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(target.lon, target.lat),
                    Point.fromLngLat(displayCoordinate.longitude, displayCoordinate.latitude)
                )
            )
        )
        feature.addStringProperty(AdsbGeoJsonMapper.PROP_ICAO24, target.id.raw)
        features += feature
    }
    return features.toTypedArray()
}
