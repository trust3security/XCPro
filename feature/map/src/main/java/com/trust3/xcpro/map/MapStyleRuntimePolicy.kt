package com.trust3.xcpro.map

internal fun resolveEffectiveMapStyleKey(
    baseStyleKey: String,
    forecastSatelliteOverrideEnabled: Boolean,
    thermallingContrastOverrideEnabled: Boolean
): String {
    return when {
        forecastSatelliteOverrideEnabled -> MapStyleCatalog.SATELLITE
        thermallingContrastOverrideEnabled -> MapStyleCatalog.THERMALLING_CONTRAST
        else -> MapStyleCatalog.normalizeBaseStyleKey(baseStyleKey)
    }
}

internal data class MapStyleMutation(
    val baseStyleChanged: Boolean,
    val effectiveStyleChanged: Boolean
)
