package com.trust3.xcpro.map.ui

import com.trust3.xcpro.common.units.AltitudeUnit
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.map.AdsbTrafficUiModel
import com.trust3.xcpro.map.Icao24
import com.trust3.xcpro.map.OgnDisplayUpdateMode
import com.trust3.xcpro.map.OgnGliderTrailSegment
import com.trust3.xcpro.map.OgnThermalHotspot
import com.trust3.xcpro.map.OgnTrafficTarget
import com.trust3.xcpro.map.SelectedOgnThermalOverlayContext
import com.trust3.xcpro.map.TrafficMapCoordinate

interface TrafficOverlayRenderPort {
    fun setOgnDisplayUpdateMode(mode: OgnDisplayUpdateMode)
    fun updateOgnTrafficTargets(
        targets: List<OgnTrafficTarget>,
        selectedTargetKey: String?,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit,
        unitsPreferences: UnitsPreferences
    )

    fun updateOgnThermalHotspots(hotspots: List<OgnThermalHotspot>)
    fun updateOgnGliderTrailSegments(segments: List<OgnGliderTrailSegment>)
    fun updateSelectedOgnThermalContext(context: SelectedOgnThermalOverlayContext?)
    fun updateOgnTargetVisuals(
        enabled: Boolean,
        resolvedTarget: OgnTrafficTarget?,
        ownshipCoordinate: TrafficMapCoordinate?,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit,
        unitsPreferences: UnitsPreferences
    )

    fun setOgnIconSizePx(iconSizePx: Int)
    fun updateAdsbTrafficTargets(
        targets: List<AdsbTrafficUiModel>,
        selectedTargetId: Icao24?,
        ownshipAltitudeMeters: Double?,
        unitsPreferences: UnitsPreferences
    )

    fun setAdsbIconSizePx(iconSizePx: Int)
    fun setAdsbEmergencyFlashEnabled(enabled: Boolean)
    fun setAdsbDefaultMediumUnknownIconEnabled(enabled: Boolean)
}

data class MapTrafficOverlayReadyConfig(
    val ognDisplayUpdateMode: OgnDisplayUpdateMode,
    val ognIconSizePx: Int,
    val adsbIconSizePx: Int,
    val adsbEmergencyFlashEnabled: Boolean,
    val adsbDefaultMediumUnknownIconEnabled: Boolean
)

fun applyMapReadyTrafficOverlayConfig(
    port: TrafficOverlayRenderPort,
    config: MapTrafficOverlayReadyConfig
) {
    port.setOgnDisplayUpdateMode(config.ognDisplayUpdateMode)
    port.setOgnIconSizePx(config.ognIconSizePx)
    port.setAdsbIconSizePx(config.adsbIconSizePx)
    port.setAdsbEmergencyFlashEnabled(config.adsbEmergencyFlashEnabled)
    port.setAdsbDefaultMediumUnknownIconEnabled(config.adsbDefaultMediumUnknownIconEnabled)
}
