package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.map.AdsbSelectedTargetDetails
import com.example.xcpro.map.AdsbTrafficSnapshot
import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.MapPoint
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapStateReader
import com.example.xcpro.map.OgnDisplayUpdateMode
import com.example.xcpro.map.OgnGliderTrailSegment
import com.example.xcpro.map.OgnThermalHotspot
import com.example.xcpro.map.OgnTrafficSnapshot
import com.example.xcpro.map.OgnTrafficTarget
import com.example.xcpro.map.SelectedOgnThermalContext
import com.example.xcpro.map.model.GpsStatusUiModel
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import com.example.xcpro.replay.SessionState
import com.example.xcpro.tasks.TaskFlightSurfaceUiState
import com.example.xcpro.tasks.core.TaskType

internal data class MapScreenMapBindings(
    val mapStyleName: String,
    val gpsStatus: GpsStatusUiModel,
    val showRecenterButton: Boolean,
    val showReturnButton: Boolean,
    val currentMode: FlightMode,
    val showDistanceCircles: Boolean
)

internal data class MapScreenSessionBindings(
    val replaySession: SessionState,
    val suppressLiveGps: Boolean,
    val allowSensorStart: Boolean,
    val trailSettings: TrailSettings,
    val trailUpdateResult: TrailUpdateResult?
)

internal data class MapScreenTaskBindings(
    val isAATEditMode: Boolean,
    val taskType: TaskType,
    val taskFlightSurfaceUiState: TaskFlightSurfaceUiState,
    val savedLocation: MapPoint?,
    val savedZoom: Double?,
    val savedBearing: Double?,
    val hasInitiallyCentered: Boolean
)

@Composable
internal fun rememberMapScreenMapBindings(
    mapViewModel: MapScreenViewModel,
    mapStateReader: MapStateReader
): MapScreenMapBindings {
    val mapStyleName by mapStateReader.mapStyleName.collectAsStateWithLifecycle()
    val gpsStatus by mapViewModel.gpsStatusFlow.collectAsStateWithLifecycle()
    val showRecenterButton by mapStateReader.showRecenterButton.collectAsStateWithLifecycle()
    val showReturnButton by mapStateReader.showReturnButton.collectAsStateWithLifecycle()
    val currentMode by mapStateReader.currentMode.collectAsStateWithLifecycle()
    val showDistanceCircles by mapStateReader.showDistanceCircles.collectAsStateWithLifecycle()

    return MapScreenMapBindings(
        mapStyleName = mapStyleName,
        gpsStatus = gpsStatus,
        showRecenterButton = showRecenterButton,
        showReturnButton = showReturnButton,
        currentMode = currentMode,
        showDistanceCircles = showDistanceCircles
    )
}

@Composable
internal fun rememberMapScreenSessionBindings(
    mapViewModel: MapScreenViewModel
): MapScreenSessionBindings {
    val replaySession by mapViewModel.replaySessionState.collectAsStateWithLifecycle()
    val suppressLiveGps by mapViewModel.suppressLiveGps.collectAsStateWithLifecycle()
    val allowSensorStart by mapViewModel.allowSensorStart.collectAsStateWithLifecycle()
    val trailSettings by mapViewModel.trailSettings.collectAsStateWithLifecycle()
    val trailUpdateResult by mapViewModel.trailUpdates.collectAsStateWithLifecycle()

    return MapScreenSessionBindings(
        replaySession = replaySession,
        suppressLiveGps = suppressLiveGps,
        allowSensorStart = allowSensorStart,
        trailSettings = trailSettings,
        trailUpdateResult = trailUpdateResult
    )
}

@Composable
internal fun rememberMapScreenTaskBindings(
    mapViewModel: MapScreenViewModel,
    mapStateReader: MapStateReader
): MapScreenTaskBindings {
    val isAATEditMode by mapViewModel.isAATEditMode.collectAsStateWithLifecycle()
    val taskType by mapViewModel.taskType.collectAsStateWithLifecycle()
    val taskFlightSurfaceUiState by mapViewModel.taskFlightSurfaceUiState.collectAsStateWithLifecycle()
    val savedLocation by mapStateReader.savedLocation.collectAsStateWithLifecycle()
    val savedZoom by mapStateReader.savedZoom.collectAsStateWithLifecycle()
    val savedBearing by mapStateReader.savedBearing.collectAsStateWithLifecycle()
    val hasInitiallyCentered by mapStateReader.hasInitiallyCentered.collectAsStateWithLifecycle()

    return MapScreenTaskBindings(
        isAATEditMode = isAATEditMode,
        taskType = taskType,
        taskFlightSurfaceUiState = taskFlightSurfaceUiState,
        savedLocation = savedLocation,
        savedZoom = savedZoom,
        savedBearing = savedBearing,
        hasInitiallyCentered = hasInitiallyCentered
    )
}

@Composable
internal fun rememberMapScreenTrafficBinding(
    mapViewModel: MapScreenViewModel
): MapTrafficUiBinding {
    val ognTargets by mapViewModel.ognTargets.collectAsStateWithLifecycle()
    val ognSnapshot by mapViewModel.ognSnapshot.collectAsStateWithLifecycle()
    val ognOverlayEnabled by mapViewModel.ognOverlayEnabled.collectAsStateWithLifecycle()
    val ognIconSizePx by mapViewModel.ognIconSizePx.collectAsStateWithLifecycle()
    val ognDisplayUpdateMode by mapViewModel.ognDisplayUpdateMode.collectAsStateWithLifecycle()
    val ognThermalHotspots by mapViewModel.ognThermalHotspots.collectAsStateWithLifecycle()
    val showOgnSciaEnabled by mapViewModel.showOgnSciaEnabled.collectAsStateWithLifecycle()
    val ognTargetEnabled by mapViewModel.ognTargetEnabled.collectAsStateWithLifecycle()
    val ognTargetAircraftKey by mapViewModel.ognTargetAircraftKey.collectAsStateWithLifecycle()
    val ognResolvedTarget by mapViewModel.ognResolvedTarget.collectAsStateWithLifecycle()
    val showOgnThermalsEnabled by mapViewModel.showOgnThermalsEnabled.collectAsStateWithLifecycle()
    val ognGliderTrailSegments by mapViewModel.ognGliderTrailSegments.collectAsStateWithLifecycle()
    val ownshipAltitudeMeters by mapViewModel.ownshipAltitudeMeters.collectAsStateWithLifecycle()
    val ognAltitudeUnit by mapViewModel.ognAltitudeUnit.collectAsStateWithLifecycle()
    val adsbTargets by mapViewModel.adsbTargets.collectAsStateWithLifecycle()
    val adsbSnapshot by mapViewModel.adsbSnapshot.collectAsStateWithLifecycle()
    val adsbOverlayEnabled by mapViewModel.adsbOverlayEnabled.collectAsStateWithLifecycle()
    val adsbIconSizePx by mapViewModel.adsbIconSizePx.collectAsStateWithLifecycle()
    val adsbEmergencyFlashEnabled by mapViewModel.adsbEmergencyFlashEnabled.collectAsStateWithLifecycle()
    val adsbDefaultMediumUnknownIconEnabled by mapViewModel.adsbDefaultMediumUnknownIconEnabled.collectAsStateWithLifecycle()
    val selectedOgnTarget by mapViewModel.selectedOgnTarget.collectAsStateWithLifecycle()
    val selectedOgnThermal by mapViewModel.selectedOgnThermal.collectAsStateWithLifecycle()
    val selectedOgnThermalContext by mapViewModel.selectedOgnThermalContext.collectAsStateWithLifecycle()
    val selectedAdsbTarget by mapViewModel.selectedAdsbTarget.collectAsStateWithLifecycle()

    return buildMapTrafficUiBinding(
        ognTargets = ognTargets,
        ognSnapshot = ognSnapshot,
        ognOverlayEnabled = ognOverlayEnabled,
        ognIconSizePx = ognIconSizePx,
        ognDisplayUpdateMode = ognDisplayUpdateMode,
        ognThermalHotspots = ognThermalHotspots,
        showOgnSciaEnabled = showOgnSciaEnabled,
        ognTargetEnabled = ognTargetEnabled,
        ognTargetAircraftKey = ognTargetAircraftKey,
        ognResolvedTarget = ognResolvedTarget,
        showOgnThermalsEnabled = showOgnThermalsEnabled,
        ognGliderTrailSegments = ognGliderTrailSegments,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        ognAltitudeUnit = ognAltitudeUnit,
        adsbTargets = adsbTargets,
        adsbSnapshot = adsbSnapshot,
        adsbOverlayEnabled = adsbOverlayEnabled,
        adsbIconSizePx = adsbIconSizePx,
        adsbEmergencyFlashEnabled = adsbEmergencyFlashEnabled,
        adsbDefaultMediumUnknownIconEnabled = adsbDefaultMediumUnknownIconEnabled,
        selectedOgnTarget = selectedOgnTarget,
        selectedOgnThermal = selectedOgnThermal,
        selectedOgnThermalContext = selectedOgnThermalContext,
        selectedAdsbTarget = selectedAdsbTarget
    )
}

internal fun buildMapTrafficUiBinding(
    ognTargets: List<OgnTrafficTarget>,
    ognSnapshot: OgnTrafficSnapshot,
    ognOverlayEnabled: Boolean,
    ognIconSizePx: Int,
    ognDisplayUpdateMode: OgnDisplayUpdateMode,
    ognThermalHotspots: List<OgnThermalHotspot>,
    showOgnSciaEnabled: Boolean,
    ognTargetEnabled: Boolean,
    ognTargetAircraftKey: String?,
    ognResolvedTarget: OgnTrafficTarget?,
    showOgnThermalsEnabled: Boolean,
    ognGliderTrailSegments: List<OgnGliderTrailSegment>,
    ownshipAltitudeMeters: Double?,
    ognAltitudeUnit: AltitudeUnit,
    adsbTargets: List<AdsbTrafficUiModel>,
    adsbSnapshot: AdsbTrafficSnapshot,
    adsbOverlayEnabled: Boolean,
    adsbIconSizePx: Int,
    adsbEmergencyFlashEnabled: Boolean,
    adsbDefaultMediumUnknownIconEnabled: Boolean,
    selectedOgnTarget: OgnTrafficTarget?,
    selectedOgnThermal: OgnThermalHotspot?,
    selectedOgnThermalContext: SelectedOgnThermalContext?,
    selectedAdsbTarget: AdsbSelectedTargetDetails?
): MapTrafficUiBinding =
    MapTrafficUiBinding(
        ognTargets = ognTargets,
        ognSnapshot = ognSnapshot,
        ognOverlayEnabled = ognOverlayEnabled,
        ognIconSizePx = ognIconSizePx,
        ognDisplayUpdateMode = ognDisplayUpdateMode,
        ognThermalHotspots = ognThermalHotspots,
        showOgnSciaEnabled = showOgnSciaEnabled,
        ognTargetEnabled = ognTargetEnabled,
        ognTargetAircraftKey = ognTargetAircraftKey,
        ognResolvedTarget = ognResolvedTarget,
        showOgnThermalsEnabled = showOgnThermalsEnabled,
        ognGliderTrailSegments = ognGliderTrailSegments,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        ognAltitudeUnit = ognAltitudeUnit,
        adsbTargets = adsbTargets,
        adsbSnapshot = adsbSnapshot,
        adsbOverlayEnabled = adsbOverlayEnabled,
        adsbIconSizePx = adsbIconSizePx,
        adsbEmergencyFlashEnabled = adsbEmergencyFlashEnabled,
        adsbDefaultMediumUnknownIconEnabled = adsbDefaultMediumUnknownIconEnabled,
        selectedOgnTarget = selectedOgnTarget,
        selectedOgnThermal = selectedOgnThermal,
        selectedOgnThermalContext = selectedOgnThermalContext,
        selectedAdsbTarget = selectedAdsbTarget
    )
