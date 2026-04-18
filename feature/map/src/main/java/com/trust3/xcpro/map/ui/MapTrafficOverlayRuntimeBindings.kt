package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.trust3.xcpro.common.units.AltitudeUnit
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.map.AdsbTrafficUiModel
import com.trust3.xcpro.map.Icao24
import com.trust3.xcpro.map.MapOverlayManager
import com.trust3.xcpro.map.MapScreenViewModel
import com.trust3.xcpro.map.OgnDisplayUpdateMode
import com.trust3.xcpro.map.OgnGliderTrailSegment
import com.trust3.xcpro.map.OgnThermalHotspot
import com.trust3.xcpro.map.OgnTrafficTarget
import com.trust3.xcpro.map.SelectedOgnThermalContext
import com.trust3.xcpro.map.TrafficMapCoordinate
import com.trust3.xcpro.map.model.MapLocationUiModel
import kotlinx.coroutines.flow.StateFlow

internal data class MapTrafficOverlayRuntimeInputs(
    val ognTargets: StateFlow<List<OgnTrafficTarget>>,
    val ognOverlayEnabled: StateFlow<Boolean>,
    val ognIconSizePx: StateFlow<Int>,
    val ognDisplayUpdateMode: StateFlow<OgnDisplayUpdateMode>,
    val ognThermalHotspots: StateFlow<List<OgnThermalHotspot>>,
    val showOgnSciaEnabled: StateFlow<Boolean>,
    val ognTargetEnabled: StateFlow<Boolean>,
    val ognResolvedTarget: StateFlow<OgnTrafficTarget?>,
    val showOgnThermalsEnabled: StateFlow<Boolean>,
    val ognGliderTrailSegments: StateFlow<List<OgnGliderTrailSegment>>,
    val overlayOwnshipAltitudeMeters: StateFlow<Double?>,
    val ognAltitudeUnit: StateFlow<AltitudeUnit>,
    val adsbTargets: StateFlow<List<AdsbTrafficUiModel>>,
    val adsbOverlayEnabled: StateFlow<Boolean>,
    val adsbIconSizePx: StateFlow<Int>,
    val adsbEmergencyFlashEnabled: StateFlow<Boolean>,
    val adsbDefaultMediumUnknownIconEnabled: StateFlow<Boolean>,
    val selectedOgnTargetKey: StateFlow<String?>,
    val selectedAdsbTargetId: StateFlow<Icao24?>,
    val selectedOgnThermalContext: StateFlow<SelectedOgnThermalContext?>,
    val unitsPreferences: StateFlow<UnitsPreferences>,
    val currentLocation: StateFlow<MapLocationUiModel?>
)

internal interface TrafficOverlayCollectorTelemetrySink {
    fun onOgnTrafficCollectorEmission()
    fun onOgnTrafficCollectorDeduped()
    fun onOgnTrafficPortUpdate()
    fun onOgnTargetVisualCollectorEmission()
    fun onOgnTargetVisualCollectorDeduped()
    fun onOgnTargetVisualPortUpdate()
    fun onAdsbTrafficCollectorEmission()
    fun onAdsbTrafficCollectorDeduped()
    fun onAdsbTrafficPortUpdate()
    fun onOgnThermalCollectorEmission()
    fun onOgnTrailCollectorEmission()
    fun onSelectedOgnThermalCollectorEmission()
}

internal object NoOpTrafficOverlayCollectorTelemetrySink : TrafficOverlayCollectorTelemetrySink {
    override fun onOgnTrafficCollectorEmission() = Unit
    override fun onOgnTrafficCollectorDeduped() = Unit
    override fun onOgnTrafficPortUpdate() = Unit
    override fun onOgnTargetVisualCollectorEmission() = Unit
    override fun onOgnTargetVisualCollectorDeduped() = Unit
    override fun onOgnTargetVisualPortUpdate() = Unit
    override fun onAdsbTrafficCollectorEmission() = Unit
    override fun onAdsbTrafficCollectorDeduped() = Unit
    override fun onAdsbTrafficPortUpdate() = Unit
    override fun onOgnThermalCollectorEmission() = Unit
    override fun onOgnTrailCollectorEmission() = Unit
    override fun onSelectedOgnThermalCollectorEmission() = Unit
}

@Composable
internal fun rememberMapTrafficOverlayRuntimeInputs(
    mapViewModel: MapScreenViewModel,
    currentLocation: StateFlow<MapLocationUiModel?>
): MapTrafficOverlayRuntimeInputs = remember(mapViewModel, currentLocation) {
    createMapTrafficOverlayRuntimeInputs(
        mapViewModel = mapViewModel,
        currentLocation = currentLocation
    )
}

internal fun createMapTrafficOverlayRuntimeInputs(
    mapViewModel: MapScreenViewModel,
    currentLocation: StateFlow<MapLocationUiModel?>
): MapTrafficOverlayRuntimeInputs = MapTrafficOverlayRuntimeInputs(
    ognTargets = mapViewModel.ognTargets,
    ognOverlayEnabled = mapViewModel.ognOverlayEnabled,
    ognIconSizePx = mapViewModel.ognIconSizePx,
    ognDisplayUpdateMode = mapViewModel.ognDisplayUpdateMode,
    ognThermalHotspots = mapViewModel.ognThermalHotspots,
    showOgnSciaEnabled = mapViewModel.showOgnSciaEnabled,
    ognTargetEnabled = mapViewModel.ognTargetEnabled,
    ognResolvedTarget = mapViewModel.ognResolvedTarget,
    showOgnThermalsEnabled = mapViewModel.showOgnThermalsEnabled,
    ognGliderTrailSegments = mapViewModel.ognGliderTrailSegments,
    overlayOwnshipAltitudeMeters = mapViewModel.overlayOwnshipAltitudeMeters,
    ognAltitudeUnit = mapViewModel.ognAltitudeUnit,
    adsbTargets = mapViewModel.adsbTargets,
    adsbOverlayEnabled = mapViewModel.adsbOverlayEnabled,
    adsbIconSizePx = mapViewModel.adsbIconSizePx,
    adsbEmergencyFlashEnabled = mapViewModel.adsbEmergencyFlashEnabled,
    adsbDefaultMediumUnknownIconEnabled = mapViewModel.adsbDefaultMediumUnknownIconEnabled,
    selectedOgnTargetKey = mapViewModel.selectedOgnTargetKey,
    selectedAdsbTargetId = mapViewModel.selectedAdsbId,
    selectedOgnThermalContext = mapViewModel.selectedOgnThermalContext,
    unitsPreferences = mapViewModel.unitsPreferencesFlow,
    currentLocation = currentLocation
)

internal fun createMapReadyTrafficOverlayConfig(
    inputs: MapTrafficOverlayRuntimeInputs
): MapTrafficOverlayReadyConfig = MapTrafficOverlayReadyConfig(
    ognDisplayUpdateMode = inputs.ognDisplayUpdateMode.value,
    ognIconSizePx = inputs.ognIconSizePx.value,
    adsbIconSizePx = inputs.adsbIconSizePx.value,
    adsbEmergencyFlashEnabled = inputs.adsbEmergencyFlashEnabled.value,
    adsbDefaultMediumUnknownIconEnabled = inputs.adsbDefaultMediumUnknownIconEnabled.value
)

internal fun createTrafficOverlayCollectorTelemetrySink(
    overlayManager: MapOverlayManager
): TrafficOverlayCollectorTelemetrySink = object : TrafficOverlayCollectorTelemetrySink {
    override fun onOgnTrafficCollectorEmission() {
        overlayManager.recordOgnTrafficCollectorEmission()
    }

    override fun onOgnTrafficCollectorDeduped() {
        overlayManager.recordOgnTrafficCollectorDeduped()
    }

    override fun onOgnTrafficPortUpdate() {
        overlayManager.recordOgnTrafficPortUpdate()
    }

    override fun onOgnTargetVisualCollectorEmission() {
        overlayManager.recordOgnTargetVisualCollectorEmission()
    }

    override fun onOgnTargetVisualCollectorDeduped() {
        overlayManager.recordOgnTargetVisualCollectorDeduped()
    }

    override fun onOgnTargetVisualPortUpdate() {
        overlayManager.recordOgnTargetVisualPortUpdate()
    }

    override fun onAdsbTrafficCollectorEmission() {
        overlayManager.recordAdsbTrafficCollectorEmission()
    }

    override fun onAdsbTrafficCollectorDeduped() {
        overlayManager.recordAdsbTrafficCollectorDeduped()
    }

    override fun onAdsbTrafficPortUpdate() {
        overlayManager.recordAdsbTrafficPortUpdate()
    }

    override fun onOgnThermalCollectorEmission() {
        overlayManager.recordOgnThermalCollectorEmission()
    }

    override fun onOgnTrailCollectorEmission() {
        overlayManager.recordOgnTrailCollectorEmission()
    }

    override fun onSelectedOgnThermalCollectorEmission() {
        overlayManager.recordSelectedOgnThermalCollectorEmission()
    }
}

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
    override fun setOgnDisplayUpdateMode(mode: com.trust3.xcpro.map.OgnDisplayUpdateMode) {
        overlayManager.setOgnDisplayUpdateMode(mode)
    }

    override fun updateOgnTrafficTargets(
        targets: List<com.trust3.xcpro.map.OgnTrafficTarget>,
        selectedTargetKey: String?,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: com.trust3.xcpro.common.units.AltitudeUnit,
        unitsPreferences: UnitsPreferences
    ) {
        overlayManager.updateOgnTrafficTargets(
            targets = targets,
            selectedTargetKey = selectedTargetKey,
            ownshipAltitudeMeters = ownshipAltitudeMeters,
            altitudeUnit = altitudeUnit,
            unitsPreferences = unitsPreferences
        )
    }

    override fun updateOgnThermalHotspots(hotspots: List<com.trust3.xcpro.map.OgnThermalHotspot>) {
        overlayManager.updateOgnThermalHotspots(hotspots)
    }

    override fun updateOgnGliderTrailSegments(segments: List<com.trust3.xcpro.map.OgnGliderTrailSegment>) {
        overlayManager.updateOgnGliderTrailSegments(segments)
    }

    override fun updateSelectedOgnThermalContext(
        context: com.trust3.xcpro.map.SelectedOgnThermalOverlayContext?
    ) {
        overlayManager.updateSelectedOgnThermalContext(context)
    }

    override fun updateOgnTargetVisuals(
        enabled: Boolean,
        resolvedTarget: com.trust3.xcpro.map.OgnTrafficTarget?,
        ownshipCoordinate: TrafficMapCoordinate?,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: com.trust3.xcpro.common.units.AltitudeUnit,
        unitsPreferences: UnitsPreferences
    ) {
        overlayManager.updateOgnTargetVisuals(
            enabled = enabled,
            resolvedTarget = resolvedTarget,
            ownshipCoordinate = ownshipCoordinate,
            ownshipAltitudeMeters = ownshipAltitudeMeters,
            altitudeUnit = altitudeUnit,
            unitsPreferences = unitsPreferences
        )
    }

    override fun setOgnIconSizePx(iconSizePx: Int) {
        overlayManager.setOgnIconSizePx(iconSizePx)
    }

    override fun updateAdsbTrafficTargets(
        targets: List<com.trust3.xcpro.map.AdsbTrafficUiModel>,
        selectedTargetId: com.trust3.xcpro.map.Icao24?,
        ownshipAltitudeMeters: Double?,
        unitsPreferences: UnitsPreferences
    ) {
        overlayManager.updateAdsbTrafficTargets(
            targets = targets,
            selectedTargetId = selectedTargetId,
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
