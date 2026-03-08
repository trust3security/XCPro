package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.AdsbSelectedTargetDetails
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapStateReader
import com.example.xcpro.map.MapStateStore
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.OgnGliderTrailSegment
import com.example.xcpro.ogn.OgnDisplayUpdateMode
import com.example.xcpro.replay.SessionState
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.model.GpsStatusUiModel
import com.example.xcpro.tasks.core.TaskType

internal data class MapScreenBindings(
    val mapStyleName: String,
    val gpsStatus: GpsStatusUiModel,
    val showRecenterButton: Boolean,
    val showReturnButton: Boolean,
    val currentMode: FlightMode,
    val currentZoom: Float,
    val replaySession: SessionState,
    val suppressLiveGps: Boolean,
    val allowSensorStart: Boolean,
    val locationForUi: MapLocationUiModel?,
    val trailSettings: TrailSettings,
    val trailUpdateResult: TrailUpdateResult?,
    val ognTargets: List<OgnTrafficTarget>,
    val ognSnapshot: OgnTrafficSnapshot,
    val ognOverlayEnabled: Boolean,
    val ognIconSizePx: Int,
    val ognDisplayUpdateMode: OgnDisplayUpdateMode,
    val ognThermalHotspots: List<OgnThermalHotspot>,
    val showOgnSciaEnabled: Boolean,
    val ognTargetEnabled: Boolean,
    val ognTargetAircraftKey: String?,
    val ognResolvedTarget: OgnTrafficTarget?,
    val showOgnThermalsEnabled: Boolean,
    val ognGliderTrailSegments: List<OgnGliderTrailSegment>,
    val ownshipAltitudeMetersForOgn: Double?,
    val ognAltitudeUnit: AltitudeUnit,
    val adsbTargets: List<AdsbTrafficUiModel>,
    val adsbSnapshot: AdsbTrafficSnapshot,
    val adsbOverlayEnabled: Boolean,
    val adsbIconSizePx: Int,
    val adsbEmergencyFlashEnabled: Boolean,
    val adsbDefaultMediumUnknownIconEnabled: Boolean,
    val selectedOgnTarget: OgnTrafficTarget?,
    val selectedOgnThermal: OgnThermalHotspot?,
    val selectedAdsbTarget: AdsbSelectedTargetDetails?,
    val isAATEditMode: Boolean,
    val taskType: TaskType,
    val savedLocation: MapStateStore.MapPoint?,
    val savedZoom: Double?,
    val savedBearing: Double?,
    val hasInitiallyCentered: Boolean,
    val showDistanceCircles: Boolean
)

@Composable
internal fun rememberMapScreenBindings(
    mapViewModel: MapScreenViewModel,
    mapStateReader: MapStateReader
): MapScreenBindings {
    val mapStyleName by mapStateReader.mapStyleName.collectAsStateWithLifecycle()
    val gpsStatus by mapViewModel.gpsStatusFlow.collectAsStateWithLifecycle()
    val showRecenterButton by mapStateReader.showRecenterButton.collectAsStateWithLifecycle()
    val showReturnButton by mapStateReader.showReturnButton.collectAsStateWithLifecycle()
    val currentMode by mapStateReader.currentMode.collectAsStateWithLifecycle()
    val currentZoom by mapStateReader.currentZoom.collectAsStateWithLifecycle()
    val replaySession by mapViewModel.replaySessionState.collectAsStateWithLifecycle()
    val suppressLiveGps by mapViewModel.suppressLiveGps.collectAsStateWithLifecycle()
    val allowSensorStart by mapViewModel.allowSensorStart.collectAsStateWithLifecycle()
    val locationForUi by mapViewModel.mapLocation.collectAsStateWithLifecycle()
    val trailSettings by mapStateReader.trailSettings.collectAsStateWithLifecycle()
    val trailUpdateResult by mapViewModel.trailUpdates.collectAsStateWithLifecycle()
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
    val ownshipAltitudeMetersForOgn by mapViewModel.ownshipAltitudeMeters.collectAsStateWithLifecycle()
    val ognAltitudeUnit by mapViewModel.ognAltitudeUnit.collectAsStateWithLifecycle()
    val adsbTargets by mapViewModel.adsbTargets.collectAsStateWithLifecycle()
    val adsbSnapshot by mapViewModel.adsbSnapshot.collectAsStateWithLifecycle()
    val adsbOverlayEnabled by mapViewModel.adsbOverlayEnabled.collectAsStateWithLifecycle()
    val adsbIconSizePx by mapViewModel.adsbIconSizePx.collectAsStateWithLifecycle()
    val adsbEmergencyFlashEnabled by mapViewModel.adsbEmergencyFlashEnabled.collectAsStateWithLifecycle()
    val adsbDefaultMediumUnknownIconEnabled by mapViewModel.adsbDefaultMediumUnknownIconEnabled.collectAsStateWithLifecycle()
    val selectedOgnTarget by mapViewModel.selectedOgnTarget.collectAsStateWithLifecycle()
    val selectedOgnThermal by mapViewModel.selectedOgnThermal.collectAsStateWithLifecycle()
    val selectedAdsbTarget by mapViewModel.selectedAdsbTarget.collectAsStateWithLifecycle()
    val isAATEditMode by mapViewModel.isAATEditMode.collectAsStateWithLifecycle()
    val taskType by mapViewModel.taskType.collectAsStateWithLifecycle()
    val savedLocation by mapStateReader.savedLocation.collectAsStateWithLifecycle()
    val savedZoom by mapStateReader.savedZoom.collectAsStateWithLifecycle()
    val savedBearing by mapStateReader.savedBearing.collectAsStateWithLifecycle()
    val hasInitiallyCentered by mapStateReader.hasInitiallyCentered.collectAsStateWithLifecycle()
    val showDistanceCircles by mapStateReader.showDistanceCircles.collectAsStateWithLifecycle()

    return MapScreenBindings(
        mapStyleName = mapStyleName,
        gpsStatus = gpsStatus,
        showRecenterButton = showRecenterButton,
        showReturnButton = showReturnButton,
        currentMode = currentMode,
        currentZoom = currentZoom,
        replaySession = replaySession,
        suppressLiveGps = suppressLiveGps,
        allowSensorStart = allowSensorStart,
        locationForUi = locationForUi,
        trailSettings = trailSettings,
        trailUpdateResult = trailUpdateResult,
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
        ownshipAltitudeMetersForOgn = ownshipAltitudeMetersForOgn,
        ognAltitudeUnit = ognAltitudeUnit,
        adsbTargets = adsbTargets,
        adsbSnapshot = adsbSnapshot,
        adsbOverlayEnabled = adsbOverlayEnabled,
        adsbIconSizePx = adsbIconSizePx,
        adsbEmergencyFlashEnabled = adsbEmergencyFlashEnabled,
        adsbDefaultMediumUnknownIconEnabled = adsbDefaultMediumUnknownIconEnabled,
        selectedOgnTarget = selectedOgnTarget,
        selectedOgnThermal = selectedOgnThermal,
        selectedAdsbTarget = selectedAdsbTarget,
        isAATEditMode = isAATEditMode,
        taskType = taskType,
        savedLocation = savedLocation,
        savedZoom = savedZoom,
        savedBearing = savedBearing,
        hasInitiallyCentered = hasInitiallyCentered,
        showDistanceCircles = showDistanceCircles
    )
}
