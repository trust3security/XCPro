package com.trust3.xcpro.map.ui

import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.example.dfcards.dfcards.FlightDataViewModel
import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.gestures.TaskGestureCallbacks
import com.trust3.xcpro.gestures.TaskGestureHandler
import com.trust3.xcpro.map.FlightDataManager
import com.trust3.xcpro.map.MapCameraRuntimePort
import com.trust3.xcpro.map.MapInitializer
import com.trust3.xcpro.map.MapLocationRenderFrameBinder
import com.trust3.xcpro.map.MapLocationRuntimePort
import com.trust3.xcpro.map.MapModalManager
import com.trust3.xcpro.map.MapOverlayManager
import com.trust3.xcpro.map.MapRenderSurfaceDiagnostics
import com.trust3.xcpro.map.MapScreenState
import com.trust3.xcpro.map.MapTaskScreenManager
import com.trust3.xcpro.map.TaskRenderSnapshot
import com.trust3.xcpro.map.WindArrowUiState
import com.trust3.xcpro.map.ballast.BallastCommand
import com.trust3.xcpro.map.ballast.BallastUiState
import com.trust3.xcpro.map.model.MapLocationUiModel
import com.trust3.xcpro.map.ui.widgets.MapUIWidgetManager
import com.trust3.xcpro.qnh.QnhCalibrationState
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.tasks.TaskFlightSurfaceUiState
import com.trust3.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.variometer.layout.VariometerUiState
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.maps.MapLibreMap

internal data class MapScreenContentInputs(
    val map: MapScreenMapContentInputs,
    val overlays: MapScreenOverlayContentInputs,
    val widgets: MapScreenWidgetContentInputs,
    val replay: MapScreenReplayContentInputs
)

internal data class MapScreenMapContentInputs(
    val density: Density,
    val mapState: MapScreenState,
    val mapInitializer: MapInitializer,
    val onMapReady: (MapLibreMap) -> Unit,
    val onMapViewBound: () -> Unit,
    val locationManager: MapLocationRuntimePort,
    val locationRenderFrameBinder: MapLocationRenderFrameBinder,
    val renderSurfaceDiagnostics: MapRenderSurfaceDiagnostics,
    val flightDataManager: FlightDataManager,
    val flightViewModel: FlightDataViewModel,
    val taskType: TaskType,
    val createTaskGestureHandler: (TaskGestureCallbacks) -> TaskGestureHandler,
    val windArrowState: WindArrowUiState,
    val showWindSpeedOnVario: Boolean,
    val cameraManager: MapCameraRuntimePort,
    val currentMode: FlightMode,
    val visibleModes: List<FlightMode>,
    val currentZoom: StateFlow<Float>,
    val onModeChange: (FlightMode) -> Unit,
    val forecastSatelliteOverrideEnabled: Boolean,
    val onForecastSatelliteOverrideChanged: (Boolean) -> Unit,
    val currentLocation: StateFlow<MapLocationUiModel?>
)

internal data class MapScreenOverlayContentInputs(
    val showMapBottomNavigation: Boolean,
    val renderLocalOwnship: Boolean,
    val showRecenterButton: Boolean,
    val showReturnButton: Boolean,
    val showDistanceCircles: Boolean,
    val showPilotStatusIndicator: Boolean,
    val isGeneralSettingsVisible: Boolean,
    val traffic: MapTrafficUiBinding,
    val isUiEditMode: Boolean,
    val onEditModeChange: (Boolean) -> Unit,
    val isAATEditMode: Boolean,
    val onEnterAATEditMode: (Int) -> Unit,
    val onUpdateAATTargetPoint: (Int, Double, Double) -> Unit,
    val onExitAATEditMode: () -> Unit,
    val safeContainerSize: MutableState<IntSize>,
    val overlayManager: MapOverlayManager,
    val modalManager: MapModalManager,
    val taskScreenManager: MapTaskScreenManager,
    val taskFlightSurfaceUiState: TaskFlightSurfaceUiState,
    val taskRenderSnapshotProvider: () -> TaskRenderSnapshot,
    val watchedPilotFocusEpoch: Int,
    val mapLibreMapProvider: () -> org.maplibre.android.maps.MapLibreMap?,
    val onFocusWatchedPilot: (Double, Double) -> Boolean,
    val waypointData: List<WaypointData>,
    val unitsPreferences: UnitsPreferences,
    val qnhCalibrationState: QnhCalibrationState,
    val onAutoCalibrateQnh: () -> Unit,
    val onSetManualQnh: (Double) -> Unit,
    val trafficActions: MapTrafficUiActions,
    val ballastUiState: StateFlow<BallastUiState>,
    val isBallastPillHidden: Boolean,
    val onBallastCommand: (BallastCommand) -> Unit,
    val onHamburgerTap: () -> Unit,
    val onHamburgerLongPress: () -> Unit,
    val onSettingsTap: () -> Unit
)

internal data class MapScreenWidgetContentInputs(
    val widgetManager: MapUIWidgetManager,
    val screenWidthPx: Float,
    val screenHeightPx: Float,
    val variometerUiState: VariometerUiState,
    val minVariometerSizePx: Float,
    val maxVariometerSizePx: Float,
    val onVariometerOffsetChange: (Offset) -> Unit,
    val onVariometerSizeChange: (Float) -> Unit,
    val onVariometerLongPress: () -> Unit,
    val onVariometerEditFinished: () -> Unit,
    val hamburgerOffset: MutableState<Offset>,
    val flightModeOffset: MutableState<Offset>,
    val settingsOffset: MutableState<Offset>,
    val ballastOffset: MutableState<Offset>,
    val hamburgerSizePx: MutableState<Float>,
    val settingsSizePx: MutableState<Float>,
    val onHamburgerOffsetChange: (Offset) -> Unit,
    val onFlightModeOffsetChange: (Offset) -> Unit,
    val onSettingsOffsetChange: (Offset) -> Unit,
    val onBallastOffsetChange: (Offset) -> Unit,
    val onHamburgerSizeChange: (Float) -> Unit,
    val onSettingsSizeChange: (Float) -> Unit,
    val cardStyle: CardStyle,
    val hiddenCardIds: Set<String>
)

internal data class MapScreenReplayContentInputs(
    val replayState: StateFlow<SessionState>,
    val showVarioDemoFab: Boolean,
    val onVarioDemoSimClick: () -> Unit,
    val onVarioDemoSim2Click: () -> Unit,
    val onVarioDemoSim3Click: () -> Unit,
    val showRacingReplayFab: Boolean,
    val onRacingReplayClick: () -> Unit
)
