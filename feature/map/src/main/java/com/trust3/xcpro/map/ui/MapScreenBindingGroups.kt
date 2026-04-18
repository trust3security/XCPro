package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trust3.xcpro.map.AdsbSelectedTargetDetails
import com.trust3.xcpro.map.AdsbTrafficSnapshot
import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.map.MapPoint
import com.trust3.xcpro.map.MapScreenViewModel
import com.trust3.xcpro.map.MapStateReader
import com.trust3.xcpro.map.OgnThermalHotspot
import com.trust3.xcpro.map.OgnTrafficSnapshot
import com.trust3.xcpro.map.OgnTrafficTarget
import com.trust3.xcpro.map.SelectedOgnThermalContext
import com.trust3.xcpro.map.model.GpsStatusUiModel
import com.trust3.xcpro.map.trail.TrailSettings
import com.trust3.xcpro.map.trail.domain.TrailUpdateResult
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.tasks.TaskFlightSurfaceUiState
import com.trust3.xcpro.tasks.core.TaskType

internal data class MapScreenMapBindings(
    val mapStyleName: String,
    val baseMapStyleName: String,
    val forecastSatelliteOverrideEnabled: Boolean,
    val gpsStatus: GpsStatusUiModel,
    val showRecenterButton: Boolean,
    val showReturnButton: Boolean,
    val currentMode: FlightMode,
    val visibleModes: List<FlightMode>,
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
    val baseMapStyleName by mapViewModel.baseMapStyleName.collectAsStateWithLifecycle()
    val forecastSatelliteOverrideEnabled by mapViewModel.forecastSatelliteOverrideEnabled
        .collectAsStateWithLifecycle()
    val gpsStatus by mapViewModel.gpsStatusFlow.collectAsStateWithLifecycle()
    val showRecenterButton by mapStateReader.showRecenterButton.collectAsStateWithLifecycle()
    val showReturnButton by mapStateReader.showReturnButton.collectAsStateWithLifecycle()
    val currentMode by mapStateReader.currentMode.collectAsStateWithLifecycle()
    val visibleModes by mapViewModel.visibleFlightModes.collectAsStateWithLifecycle()
    val showDistanceCircles by mapStateReader.showDistanceCircles.collectAsStateWithLifecycle()

    return MapScreenMapBindings(
        mapStyleName = mapStyleName,
        baseMapStyleName = baseMapStyleName,
        forecastSatelliteOverrideEnabled = forecastSatelliteOverrideEnabled,
        gpsStatus = gpsStatus,
        showRecenterButton = showRecenterButton,
        showReturnButton = showReturnButton,
        currentMode = currentMode,
        visibleModes = visibleModes,
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
    val ognSnapshot by mapViewModel.ognSnapshot.collectAsStateWithLifecycle()
    val ognOverlayEnabled by mapViewModel.ognOverlayEnabled.collectAsStateWithLifecycle()
    val showOgnSciaEnabled by mapViewModel.showOgnSciaEnabled.collectAsStateWithLifecycle()
    val showOgnThermalsEnabled by mapViewModel.showOgnThermalsEnabled.collectAsStateWithLifecycle()
    val ognTargetEnabled by mapViewModel.ognTargetEnabled.collectAsStateWithLifecycle()
    val ognTargetAircraftKey by mapViewModel.ognTargetAircraftKey.collectAsStateWithLifecycle()
    val adsbSnapshot by mapViewModel.adsbSnapshot.collectAsStateWithLifecycle()
    val adsbOverlayEnabled by mapViewModel.adsbOverlayEnabled.collectAsStateWithLifecycle()
    val selectedOgnTarget by mapViewModel.selectedOgnTarget.collectAsStateWithLifecycle()
    val selectedOgnThermal by mapViewModel.selectedOgnThermal.collectAsStateWithLifecycle()
    val selectedOgnThermalDetailsVisible by mapViewModel.selectedOgnThermalDetailsVisible.collectAsStateWithLifecycle()
    val selectedOgnThermalContext by mapViewModel.selectedOgnThermalContext.collectAsStateWithLifecycle()
    val selectedAdsbTarget by mapViewModel.selectedAdsbTarget.collectAsStateWithLifecycle()

    return buildMapTrafficUiBinding(
        ognSnapshot = ognSnapshot,
        ognOverlayEnabled = ognOverlayEnabled,
        showOgnSciaEnabled = showOgnSciaEnabled,
        showOgnThermalsEnabled = showOgnThermalsEnabled,
        ognTargetEnabled = ognTargetEnabled,
        ognTargetAircraftKey = ognTargetAircraftKey,
        adsbSnapshot = adsbSnapshot,
        adsbOverlayEnabled = adsbOverlayEnabled,
        selectedOgnTarget = selectedOgnTarget,
        selectedOgnThermal = selectedOgnThermal,
        selectedOgnThermalDetailsVisible = selectedOgnThermalDetailsVisible,
        selectedOgnThermalContext = selectedOgnThermalContext,
        selectedAdsbTarget = selectedAdsbTarget
    )
}

internal fun buildMapTrafficUiBinding(
    ognSnapshot: OgnTrafficSnapshot,
    ognOverlayEnabled: Boolean,
    showOgnSciaEnabled: Boolean,
    showOgnThermalsEnabled: Boolean,
    ognTargetEnabled: Boolean,
    ognTargetAircraftKey: String?,
    adsbSnapshot: AdsbTrafficSnapshot,
    adsbOverlayEnabled: Boolean,
    selectedOgnTarget: OgnTrafficTarget?,
    selectedOgnThermal: OgnThermalHotspot?,
    selectedOgnThermalDetailsVisible: Boolean,
    selectedOgnThermalContext: SelectedOgnThermalContext?,
    selectedAdsbTarget: AdsbSelectedTargetDetails?
): MapTrafficUiBinding =
    MapTrafficUiBinding(
        ognSnapshot = ognSnapshot,
        ognOverlayEnabled = ognOverlayEnabled,
        showOgnSciaEnabled = showOgnSciaEnabled,
        showOgnThermalsEnabled = showOgnThermalsEnabled,
        ognTargetEnabled = ognTargetEnabled,
        ognTargetAircraftKey = ognTargetAircraftKey,
        adsbSnapshot = adsbSnapshot,
        adsbOverlayEnabled = adsbOverlayEnabled,
        selectedOgnTarget = selectedOgnTarget,
        selectedOgnThermal = selectedOgnThermal,
        selectedOgnThermalDetailsVisible = selectedOgnThermalDetailsVisible,
        selectedOgnThermalContext = selectedOgnThermalContext,
        selectedAdsbTarget = selectedAdsbTarget
    )
