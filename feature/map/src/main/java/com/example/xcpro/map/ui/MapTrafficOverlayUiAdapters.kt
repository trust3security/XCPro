package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.TrafficMapCoordinate

@Composable
internal fun rememberTrafficOverlayRenderState(
    traffic: MapTrafficUiBinding,
    locationForUi: com.example.xcpro.map.model.MapLocationUiModel?,
    unitsPreferences: UnitsPreferences,
    renderLocalOwnship: Boolean
): MapTrafficOverlayRenderState = remember(
    traffic.ognTargets,
    traffic.ognOverlayEnabled,
    traffic.ognThermalHotspots,
    traffic.showOgnSciaEnabled,
    traffic.ognTargetEnabled,
    traffic.ognResolvedTarget,
    locationForUi,
    traffic.showOgnThermalsEnabled,
    traffic.ognDisplayUpdateMode,
    traffic.ognGliderTrailSegments,
    traffic.ownshipAltitudeMeters,
    traffic.ognAltitudeUnit,
    unitsPreferences,
    renderLocalOwnship,
    traffic.ognIconSizePx,
    traffic.adsbTargets,
    traffic.adsbOverlayEnabled,
    traffic.adsbIconSizePx,
    traffic.adsbEmergencyFlashEnabled,
    traffic.adsbDefaultMediumUnknownIconEnabled
) {
    buildTrafficOverlayRenderState(
        traffic = traffic,
        locationForUi = locationForUi,
        unitsPreferences = unitsPreferences,
        renderLocalOwnship = renderLocalOwnship
    )
}

internal fun buildTrafficOverlayRenderState(
    traffic: MapTrafficUiBinding,
    locationForUi: com.example.xcpro.map.model.MapLocationUiModel?,
    unitsPreferences: UnitsPreferences,
    renderLocalOwnship: Boolean
): MapTrafficOverlayRenderState {
    return MapTrafficOverlayRenderState(
        ognTargets = traffic.ognTargets,
        ognOverlayEnabled = traffic.ognOverlayEnabled,
        ognThermalHotspots = traffic.ognThermalHotspots,
        showOgnSciaEnabled = traffic.showOgnSciaEnabled,
        ognTargetEnabled = traffic.ognTargetEnabled,
        ognResolvedTarget = traffic.ognResolvedTarget,
        ownshipCoordinate = locationForUi?.takeIf { renderLocalOwnship }?.let { location ->
            TrafficMapCoordinate(latitude = location.latitude, longitude = location.longitude)
        },
        showOgnThermalsEnabled = traffic.showOgnThermalsEnabled,
        ognDisplayUpdateMode = traffic.ognDisplayUpdateMode,
        ognGliderTrailSegments = traffic.ognGliderTrailSegments,
        ownshipAltitudeMeters = traffic.ownshipAltitudeMeters?.takeIf { renderLocalOwnship },
        ognAltitudeUnit = traffic.ognAltitudeUnit,
        unitsPreferences = unitsPreferences,
        ognIconSizePx = traffic.ognIconSizePx,
        adsbTargets = traffic.adsbTargets,
        adsbOverlayEnabled = traffic.adsbOverlayEnabled,
        adsbIconSizePx = traffic.adsbIconSizePx,
        adsbEmergencyFlashEnabled = traffic.adsbEmergencyFlashEnabled,
        adsbDefaultMediumUnknownIconEnabled = traffic.adsbDefaultMediumUnknownIconEnabled
    )
}

internal fun createMapReadyTrafficOverlayConfig(
    traffic: MapTrafficUiBinding
): MapTrafficOverlayReadyConfig = MapTrafficOverlayReadyConfig(
    ognDisplayUpdateMode = traffic.ognDisplayUpdateMode,
    ognIconSizePx = traffic.ognIconSizePx,
    adsbIconSizePx = traffic.adsbIconSizePx,
    adsbEmergencyFlashEnabled = traffic.adsbEmergencyFlashEnabled,
    adsbDefaultMediumUnknownIconEnabled = traffic.adsbDefaultMediumUnknownIconEnabled
)

@Composable
internal fun rememberMapTrafficUiActions(
    mapViewModel: MapScreenViewModel
): MapTrafficUiActions = remember(mapViewModel) {
    MapTrafficUiActions(
        onToggleOgnTraffic = mapViewModel::onToggleOgnTraffic,
        onToggleOgnScia = mapViewModel::onToggleOgnScia,
        onToggleOgnThermals = mapViewModel::onToggleOgnThermals,
        onSetOgnTarget = mapViewModel::onSetOgnTarget,
        onToggleAdsbTraffic = mapViewModel::onToggleAdsbTraffic,
        onOgnTargetSelected = mapViewModel::onOgnTargetSelected,
        onOgnThermalSelected = mapViewModel::onOgnThermalSelected,
        onAdsbTargetSelected = mapViewModel::onAdsbTargetSelected,
        onDismissOgnTargetDetails = mapViewModel::dismissSelectedOgnTarget,
        onDismissOgnThermalDetails = mapViewModel::dismissSelectedOgnThermal,
        onDismissAdsbTargetDetails = mapViewModel::dismissSelectedAdsbTarget
    )
}

internal fun createTrafficOverlayRenderPort(
    overlayManager: MapOverlayManager
): TrafficOverlayRenderPort = object : TrafficOverlayRenderPort {
    override fun setOgnDisplayUpdateMode(mode: com.example.xcpro.map.OgnDisplayUpdateMode) {
        overlayManager.setOgnDisplayUpdateMode(mode)
    }

    override fun updateOgnTrafficTargets(
        targets: List<com.example.xcpro.map.OgnTrafficTarget>,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: com.example.xcpro.common.units.AltitudeUnit,
        unitsPreferences: UnitsPreferences
    ) {
        overlayManager.updateOgnTrafficTargets(
            targets = targets,
            ownshipAltitudeMeters = ownshipAltitudeMeters,
            altitudeUnit = altitudeUnit,
            unitsPreferences = unitsPreferences
        )
    }

    override fun updateOgnThermalHotspots(hotspots: List<com.example.xcpro.map.OgnThermalHotspot>) {
        overlayManager.updateOgnThermalHotspots(hotspots)
    }

    override fun updateOgnGliderTrailSegments(segments: List<com.example.xcpro.map.OgnGliderTrailSegment>) {
        overlayManager.updateOgnGliderTrailSegments(segments)
    }

    override fun updateOgnTargetVisuals(
        enabled: Boolean,
        resolvedTarget: com.example.xcpro.map.OgnTrafficTarget?,
        ownshipCoordinate: TrafficMapCoordinate?
    ) {
        overlayManager.updateOgnTargetVisuals(
            enabled = enabled,
            resolvedTarget = resolvedTarget,
            ownshipCoordinate = ownshipCoordinate
        )
    }

    override fun setOgnIconSizePx(iconSizePx: Int) {
        overlayManager.setOgnIconSizePx(iconSizePx)
    }

    override fun updateAdsbTrafficTargets(
        targets: List<com.example.xcpro.map.AdsbTrafficUiModel>,
        ownshipAltitudeMeters: Double?,
        unitsPreferences: UnitsPreferences
    ) {
        overlayManager.updateAdsbTrafficTargets(
            targets = targets,
            ownshipAltitudeMeters = ownshipAltitudeMeters,
            unitsPreferences = unitsPreferences
        )
    }

    override fun setAdsbIconSizePx(iconSizePx: Int) {
        overlayManager.setAdsbIconSizePx(iconSizePx)
    }

    override fun setAdsbEmergencyFlashEnabled(enabled: Boolean) {
        overlayManager.setAdsbEmergencyFlashEnabled(enabled)
    }

    override fun setAdsbDefaultMediumUnknownIconEnabled(enabled: Boolean) {
        overlayManager.setAdsbDefaultMediumUnknownIconEnabled(enabled)
    }
}
