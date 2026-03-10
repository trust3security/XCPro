package com.example.xcpro.map


import com.example.xcpro.common.units.UnitsPreferences
import org.maplibre.geojson.Feature

internal fun buildAdsbTrafficOverlayFeatures(
    nowMonoMs: Long,
    targets: List<AdsbTrafficUiModel>,
    ownshipAltitudeMeters: Double?,
    unitsPreferences: UnitsPreferences,
    iconStyleIdOverrides: Map<String, String>,
    emergencyFlashEnabled: Boolean,
    maxTargets: Int,
    liveAlpha: Double,
    staleAlpha: Double
): Array<Feature> {
    val features = ArrayList<Feature>(maxTargets)
    for (target in targets) {
        if (features.size >= maxTargets) break
        val feature = AdsbGeoJsonMapper.toFeature(
            target = target,
            ownshipAltitudeMeters = ownshipAltitudeMeters,
            unitsPreferences = unitsPreferences,
            iconStyleIdOverride = iconStyleIdOverrides[target.id.raw]
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
