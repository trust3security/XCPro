package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.Icao24
import com.example.xcpro.map.OgnDisplayUpdateMode
import com.example.xcpro.map.OgnGliderTrailSegment
import com.example.xcpro.map.OgnThermalHotspot
import com.example.xcpro.map.OgnTrafficTarget
import com.example.xcpro.map.TrafficMapCoordinate
import kotlin.math.round

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

data class MapTrafficOverlayRenderState(
    val ognTargets: List<OgnTrafficTarget>,
    val ognOverlayEnabled: Boolean,
    val ognThermalHotspots: List<OgnThermalHotspot>,
    val showOgnSciaEnabled: Boolean,
    val ognTargetEnabled: Boolean,
    val ognResolvedTarget: OgnTrafficTarget?,
    val ownshipCoordinate: TrafficMapCoordinate?,
    val showOgnThermalsEnabled: Boolean,
    val ognDisplayUpdateMode: OgnDisplayUpdateMode,
    val ognGliderTrailSegments: List<OgnGliderTrailSegment>,
    val ownshipAltitudeMeters: Double?,
    val ognAltitudeUnit: AltitudeUnit,
    val unitsPreferences: UnitsPreferences,
    val ognIconSizePx: Int,
    val selectedOgnTargetKey: String?,
    val adsbTargets: List<AdsbTrafficUiModel>,
    val adsbOverlayEnabled: Boolean,
    val adsbIconSizePx: Int,
    val adsbEmergencyFlashEnabled: Boolean,
    val adsbDefaultMediumUnknownIconEnabled: Boolean,
    val selectedAdsbTargetId: Icao24?
)

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

@Composable
fun MapTrafficOverlayEffects(
    port: TrafficOverlayRenderPort,
    renderState: MapTrafficOverlayRenderState
) {
    val overlayOwnshipAltitudeMeters = remember(renderState.ownshipAltitudeMeters) {
        quantizeTrafficOverlayOwnshipAltitudeMeters(renderState.ownshipAltitudeMeters)
    }
    val renderedOgnTargets = if (renderState.ognOverlayEnabled) renderState.ognTargets else emptyList()
    val renderedOgnThermals = if (renderState.ognOverlayEnabled && renderState.showOgnThermalsEnabled) {
        renderState.ognThermalHotspots
    } else {
        emptyList()
    }
    val renderedOgnTrails = if (renderState.ognOverlayEnabled && renderState.showOgnSciaEnabled) {
        renderState.ognGliderTrailSegments
    } else {
        emptyList()
    }
    val renderOgnTargetEnabled = renderState.ognOverlayEnabled && renderState.ognTargetEnabled
    val renderedOgnTarget = if (renderOgnTargetEnabled) renderState.ognResolvedTarget else null
    val renderedAdsbTargets = if (renderState.adsbOverlayEnabled) renderState.adsbTargets else emptyList()

    LaunchedEffect(renderState.ognDisplayUpdateMode) {
        port.setOgnDisplayUpdateMode(renderState.ognDisplayUpdateMode)
    }
    LaunchedEffect(
        renderedOgnTargets,
        renderState.selectedOgnTargetKey,
        overlayOwnshipAltitudeMeters,
        renderState.ognAltitudeUnit,
        renderState.unitsPreferences
    ) {
        port.updateOgnTrafficTargets(
            targets = renderedOgnTargets,
            selectedTargetKey = renderState.selectedOgnTargetKey,
            ownshipAltitudeMeters = overlayOwnshipAltitudeMeters,
            altitudeUnit = renderState.ognAltitudeUnit,
            unitsPreferences = renderState.unitsPreferences
        )
    }
    LaunchedEffect(renderedOgnThermals) {
        port.updateOgnThermalHotspots(renderedOgnThermals)
    }
    LaunchedEffect(renderedOgnTrails) {
        port.updateOgnGliderTrailSegments(renderedOgnTrails)
    }
    LaunchedEffect(
        renderOgnTargetEnabled,
        renderedOgnTarget,
        renderState.ownshipCoordinate,
        overlayOwnshipAltitudeMeters,
        renderState.ognAltitudeUnit,
        renderState.unitsPreferences
    ) {
        port.updateOgnTargetVisuals(
            enabled = renderOgnTargetEnabled,
            resolvedTarget = renderedOgnTarget,
            ownshipCoordinate = renderState.ownshipCoordinate,
            ownshipAltitudeMeters = overlayOwnshipAltitudeMeters,
            altitudeUnit = renderState.ognAltitudeUnit,
            unitsPreferences = renderState.unitsPreferences
        )
    }
    LaunchedEffect(renderState.ognIconSizePx) {
        port.setOgnIconSizePx(renderState.ognIconSizePx)
    }
    LaunchedEffect(
        renderedAdsbTargets,
        renderState.selectedAdsbTargetId,
        overlayOwnshipAltitudeMeters,
        renderState.unitsPreferences
    ) {
        port.updateAdsbTrafficTargets(
            targets = renderedAdsbTargets,
            selectedTargetId = renderState.selectedAdsbTargetId,
            ownshipAltitudeMeters = overlayOwnshipAltitudeMeters,
            unitsPreferences = renderState.unitsPreferences
        )
    }
    LaunchedEffect(renderState.adsbIconSizePx) {
        port.setAdsbIconSizePx(renderState.adsbIconSizePx)
    }
    LaunchedEffect(renderState.adsbEmergencyFlashEnabled) {
        port.setAdsbEmergencyFlashEnabled(renderState.adsbEmergencyFlashEnabled)
    }
    LaunchedEffect(renderState.adsbDefaultMediumUnknownIconEnabled) {
        port.setAdsbDefaultMediumUnknownIconEnabled(renderState.adsbDefaultMediumUnknownIconEnabled)
    }
}

private fun quantizeTrafficOverlayOwnshipAltitudeMeters(
    altitudeMeters: Double?,
    quantizeStepMeters: Double = OVERLAY_OWNSHIP_ALTITUDE_QUANTIZE_STEP_METERS
): Double? {
    val altitude = altitudeMeters?.takeIf { it.isFinite() } ?: return null
    if (!quantizeStepMeters.isFinite() || quantizeStepMeters <= 0.0) return altitude
    return round(altitude / quantizeStepMeters) * quantizeStepMeters
}

private const val OVERLAY_OWNSHIP_ALTITUDE_QUANTIZE_STEP_METERS = 2.0
