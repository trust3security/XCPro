package com.example.xcpro.map.ui

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavHostController
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.map.AdsbSelectedTargetDetails
import com.example.xcpro.map.AdsbTrafficSnapshot
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.Icao24
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.OgnThermalHotspot
import com.example.xcpro.map.OgnTrafficSnapshot
import com.example.xcpro.map.OgnTrafficTarget
import com.example.xcpro.map.WindArrowUiState
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.model.GpsStatusUiModel
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.qnh.QnhCalibrationState
import com.example.xcpro.replay.SessionState
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.variometer.layout.VariometerUiState
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.maps.MapLibreMap

internal data class MapScreenScaffoldInputs(
    val drawerState: DrawerState,
    val navController: NavHostController,
    val profileExpanded: MutableState<Boolean>,
    val mapStyleExpanded: MutableState<Boolean>,
    val settingsExpanded: MutableState<Boolean>,
    val initialMapStyle: String,
    val currentMapStyleName: String,
    val onDrawerItemSelected: (String) -> Unit,
    val onMapStyleSelected: (String) -> Unit,
    val onTransientMapStyleSelected: (String) -> Unit,
    val gpsStatus: GpsStatusUiModel,
    val isLoadingWaypoints: Boolean,
    val density: Density,
    val mapState: MapScreenState,
    val mapInitializer: MapInitializer,
    val onMapReady: (MapLibreMap) -> Unit,
    val onMapViewBound: () -> Unit,
    val locationManager: LocationManager,
    val flightDataManager: FlightDataManager,
    val flightViewModel: FlightDataViewModel,
    val taskType: TaskType,
    val createTaskGestureHandler: (TaskGestureCallbacks) -> TaskGestureHandler,
    val windArrowState: WindArrowUiState,
    val showWindSpeedOnVario: Boolean,
    val cameraManager: MapCameraManager,
    val currentMode: FlightMode,
    val currentZoom: Float,
    val onModeChange: (FlightMode) -> Unit,
    val currentLocation: MapLocationUiModel?,
    val showRecenterButton: Boolean,
    val showReturnButton: Boolean,
    val showDistanceCircles: Boolean,
    val ognSnapshot: OgnTrafficSnapshot,
    val ognOverlayEnabled: Boolean,
    val ognTargetEnabled: Boolean,
    val ognTargetAircraftKey: String?,
    val ognThermalHotspots: List<OgnThermalHotspot>,
    val showOgnSciaEnabled: Boolean,
    val showOgnThermalsEnabled: Boolean,
    val adsbSnapshot: AdsbTrafficSnapshot,
    val adsbOverlayEnabled: Boolean,
    val selectedOgnTarget: OgnTrafficTarget?,
    val selectedOgnThermal: OgnThermalHotspot?,
    val selectedAdsbTarget: AdsbSelectedTargetDetails?,
    val isUiEditMode: Boolean,
    val onEditModeChange: (Boolean) -> Unit,
    val isAATEditMode: Boolean,
    val onEnterAATEditMode: (Int) -> Unit,
    val onUpdateAATTargetPoint: (Int, Double, Double) -> Unit,
    val onExitAATEditMode: () -> Unit,
    val safeContainerSize: MutableState<IntSize>,
    val overlayManager: MapOverlayManager,
    val modalManager: MapModalManager,
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
    val taskScreenManager: MapTaskScreenManager,
    val waypointData: List<WaypointData>,
    val unitsPreferences: UnitsPreferences,
    val qnhCalibrationState: QnhCalibrationState,
    val onAutoCalibrateQnh: () -> Unit,
    val onSetManualQnh: (Double) -> Unit,
    val onToggleOgnTraffic: () -> Unit,
    val onToggleOgnScia: () -> Unit,
    val onToggleOgnThermals: () -> Unit,
    val onSetOgnTarget: (String, Boolean) -> Unit,
    val onToggleAdsbTraffic: () -> Unit,
    val onOgnTargetSelected: (String) -> Unit,
    val onOgnThermalSelected: (String) -> Unit,
    val onAdsbTargetSelected: (Icao24) -> Unit,
    val onDismissOgnTargetDetails: () -> Unit,
    val onDismissOgnThermalDetails: () -> Unit,
    val onDismissAdsbTargetDetails: () -> Unit,
    val ballastUiState: StateFlow<BallastUiState>,
    val isBallastPillHidden: Boolean,
    val onBallastCommand: (BallastCommand) -> Unit,
    val onHamburgerTap: () -> Unit,
    val onHamburgerLongPress: () -> Unit,
    val onOpenGeneralSettingsFromDrawer: () -> Unit,
    val onSettingsTap: () -> Unit,
    val cardStyle: CardStyle,
    val hiddenCardIds: Set<String>,
    val replayState: StateFlow<SessionState>,
    val showVarioDemoFab: Boolean,
    val onVarioDemoReferenceClick: () -> Unit,
    val onVarioDemoSimClick: () -> Unit,
    val onVarioDemoSim2Click: () -> Unit,
    val onVarioDemoSim3Click: () -> Unit,
    val showRacingReplayFab: Boolean,
    val onRacingReplayClick: () -> Unit
)
