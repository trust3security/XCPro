package com.example.xcpro.map.ui
/**
 * Map screen body content used inside MapScreenScaffold.
 * Invariants: UI renders state only and routes mutations through the ViewModel.
 */
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapCameraRuntimePort
import com.example.xcpro.map.MapLocationRenderFrameBinder
import com.example.xcpro.map.MapLocationRuntimePort
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.TrafficMapCoordinate
import com.example.xcpro.map.WindArrowUiState
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.qnh.QnhCalibrationState
import com.example.xcpro.replay.SessionState
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.weglide.ui.WeGlideUploadPromptUiState
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.maps.MapLibreMap

@Composable
internal fun MapScreenContent(
    inputs: MapScreenContentInputs,
    weGlideUploadPrompt: WeGlideUploadPromptUiState?,
    onConfirmWeGlideUploadPrompt: () -> Unit,
    onDismissWeGlideUploadPrompt: () -> Unit
) {
    val mapInputs = inputs.map
    val overlayInputs = inputs.overlays
    val widgetInputs = inputs.widgets
    val replayInputs = inputs.replay

    val density = mapInputs.density
    val mapState = mapInputs.mapState
    val mapInitializer = mapInputs.mapInitializer
    val onMapReady = mapInputs.onMapReady
    val onMapViewBound = mapInputs.onMapViewBound
    val locationManager = mapInputs.locationManager
    val locationRenderFrameBinder = mapInputs.locationRenderFrameBinder
    val flightDataManager = mapInputs.flightDataManager
    val flightViewModel = mapInputs.flightViewModel
    val taskType = mapInputs.taskType
    val createTaskGestureHandler = mapInputs.createTaskGestureHandler
    val windArrowState = mapInputs.windArrowState
    val showWindSpeedOnVario = mapInputs.showWindSpeedOnVario
    val cameraManager = mapInputs.cameraManager
    val currentMode = mapInputs.currentMode
    val onModeChange = mapInputs.onModeChange
    val currentMapStyleName = mapInputs.currentMapStyleName
    val onTransientMapStyleSelected = mapInputs.onTransientMapStyleSelected
    val currentZoom by mapInputs.currentZoom.collectAsStateWithLifecycle()
    val currentLocation by mapInputs.currentLocation.collectAsStateWithLifecycle()

    val showMapBottomNavigation = overlayInputs.showMapBottomNavigation
    val renderLocalOwnship = overlayInputs.renderLocalOwnship
    val showRecenterButton = overlayInputs.showRecenterButton
    val showReturnButton = overlayInputs.showReturnButton
    val showDistanceCircles = overlayInputs.showDistanceCircles
    val showPilotStatusIndicator = overlayInputs.showPilotStatusIndicator
    val isGeneralSettingsVisible = overlayInputs.isGeneralSettingsVisible
    val trafficBinding = overlayInputs.traffic
    val isUiEditMode = overlayInputs.isUiEditMode
    val onEditModeChange = overlayInputs.onEditModeChange
    val isAATEditMode = overlayInputs.isAATEditMode
    val onEnterAATEditMode = overlayInputs.onEnterAATEditMode
    val onUpdateAATTargetPoint = overlayInputs.onUpdateAATTargetPoint
    val onExitAATEditMode = overlayInputs.onExitAATEditMode
    val safeContainerSize = overlayInputs.safeContainerSize
    val overlayManager = overlayInputs.overlayManager
    val modalManager = overlayInputs.modalManager
    val taskScreenManager = overlayInputs.taskScreenManager
    val taskFlightSurfaceUiState = overlayInputs.taskFlightSurfaceUiState
    val taskRenderSnapshotProvider = overlayInputs.taskRenderSnapshotProvider
    val watchedPilotFocusEpoch = overlayInputs.watchedPilotFocusEpoch
    val mapLibreMapProvider = overlayInputs.mapLibreMapProvider
    val onFocusWatchedPilot = overlayInputs.onFocusWatchedPilot
    val waypointData = overlayInputs.waypointData
    val unitsPreferences = overlayInputs.unitsPreferences
    val qnhCalibrationState = overlayInputs.qnhCalibrationState
    val onAutoCalibrateQnh = overlayInputs.onAutoCalibrateQnh
    val onSetManualQnh = overlayInputs.onSetManualQnh
    val trafficActions = overlayInputs.trafficActions
    val ballastUiState = overlayInputs.ballastUiState
    val isBallastPillHidden = overlayInputs.isBallastPillHidden
    val onBallastCommand = overlayInputs.onBallastCommand
    val onHamburgerTap = overlayInputs.onHamburgerTap
    val onHamburgerLongPress = overlayInputs.onHamburgerLongPress
    val onSettingsTap = overlayInputs.onSettingsTap

    val widgetManager = widgetInputs.widgetManager
    val screenWidthPx = widgetInputs.screenWidthPx
    val screenHeightPx = widgetInputs.screenHeightPx
    val variometerUiState = widgetInputs.variometerUiState
    val minVariometerSizePx = widgetInputs.minVariometerSizePx
    val maxVariometerSizePx = widgetInputs.maxVariometerSizePx
    val onVariometerOffsetChange = widgetInputs.onVariometerOffsetChange
    val onVariometerSizeChange = widgetInputs.onVariometerSizeChange
    val onVariometerLongPress = widgetInputs.onVariometerLongPress
    val onVariometerEditFinished = widgetInputs.onVariometerEditFinished
    val hamburgerOffset = widgetInputs.hamburgerOffset
    val flightModeOffset = widgetInputs.flightModeOffset
    val settingsOffset = widgetInputs.settingsOffset
    val ballastOffset = widgetInputs.ballastOffset
    val hamburgerSizePx = widgetInputs.hamburgerSizePx
    val settingsSizePx = widgetInputs.settingsSizePx
    val onHamburgerOffsetChange = widgetInputs.onHamburgerOffsetChange
    val onFlightModeOffsetChange = widgetInputs.onFlightModeOffsetChange
    val onSettingsOffsetChange = widgetInputs.onSettingsOffsetChange
    val onBallastOffsetChange = widgetInputs.onBallastOffsetChange
    val onHamburgerSizeChange = widgetInputs.onHamburgerSizeChange
    val onSettingsSizeChange = widgetInputs.onSettingsSizeChange
    val cardStyle = widgetInputs.cardStyle
    val hiddenCardIds = widgetInputs.hiddenCardIds

    val replayState = replayInputs.replayState
    val showVarioDemoFab = replayInputs.showVarioDemoFab
    val onVarioDemoReferenceClick = replayInputs.onVarioDemoReferenceClick
    val onVarioDemoSimClick = replayInputs.onVarioDemoSimClick
    val onVarioDemoSim2Click = replayInputs.onVarioDemoSim2Click
    val onVarioDemoSim3Click = replayInputs.onVarioDemoSim3Click
    val showRacingReplayFab = replayInputs.showRacingReplayFab
    val onRacingReplayClick = replayInputs.onRacingReplayClick

    val ognOverlayEnabled = trafficBinding.ognOverlayEnabled
    val showOgnSciaEnabled = trafficBinding.showOgnSciaEnabled
    val showOgnThermalsEnabled = trafficBinding.showOgnThermalsEnabled
    val adsbOverlayEnabled = trafficBinding.adsbOverlayEnabled
    val onToggleOgnScia = trafficActions.onToggleOgnScia
    val onToggleOgnThermals = trafficActions.onToggleOgnThermals
    val onToggleAdsbTraffic = trafficActions.onToggleAdsbTraffic
    val onOgnTargetSelected = trafficActions.onOgnTargetSelected
    val onOgnThermalSelected = trafficActions.onOgnThermalSelected
    val onAdsbTargetSelected = trafficActions.onAdsbTargetSelected
    val onDismissOgnTargetDetails = trafficActions.onDismissOgnTargetDetails
    val onDismissOgnThermalDetails = trafficActions.onDismissOgnThermalDetails
    val onDismissAdsbTargetDetails = trafficActions.onDismissAdsbTargetDetails
    val localOwnshipRenderState = remember(
        renderLocalOwnship,
        currentLocation,
        showRecenterButton,
        showReturnButton
    ) {
        resolveMapLocalOwnshipRenderState(
            renderLocalOwnship = renderLocalOwnship,
            currentLocation = currentLocation,
            showRecenterButton = showRecenterButton,
            showReturnButton = showReturnButton
        )
    }
    val visibleCurrentLocation = localOwnshipRenderState.currentLocation

    val qnhUiState = rememberMapScreenQnhUiState(
        flightDataManager = flightDataManager,
        unitsPreferences = unitsPreferences,
        qnhCalibrationState = qnhCalibrationState,
        onAutoCalibrateQnh = onAutoCalibrateQnh,
        onSetManualQnh = onSetManualQnh
    )
    val forecastWeatherState = rememberMapScreenForecastWeatherState(
        mapLibreMap = mapState.mapLibreMap,
        currentLocation = currentLocation,
        overlayManager = overlayManager
    )
    val trafficRuntimeState = rememberMapTrafficRuntimeState(
        traffic = trafficBinding,
        debugPanelsEnabled = BuildConfig.DEBUG
    )
    val trafficContentUiState = trafficRuntimeState.contentUiState
    val topEndPilotStatusOffset = remember(trafficContentUiState.connectionIndicators) {
        trafficContentUiState.connectionIndicators.followingIndicatorTopOffset()
    }
    val bottomTabsUiState = rememberMapScreenBottomTabsUiState(
        taskScreenManager = taskScreenManager,
        hasTrafficDetailsOpen = trafficContentUiState.hasTrafficDetailsOpen,
        currentMapStyleName = currentMapStyleName,
        isGeneralSettingsVisible = isGeneralSettingsVisible
    )
    val windTapUiState = rememberMapScreenWindTapUiState(
        isForecastWindArrowOverlayActive = forecastWeatherState.isForecastWindArrowOverlayActive
    )
    // Temporarily suppress replay/debug FABs on MapScreen (REF/SIM/SIM2/SIM3/TASK).
    val hideReplayDebugFabs = true
    val auxiliaryPanelsInputs = MapAuxiliaryPanelsInputs(
        mapState = mapState,
        density = density,
        tappedWindArrowCallout = windTapUiState.tappedWindArrowCallout,
        forecastWindUnitLabel = forecastWeatherState.forecastOverlayState.windLegend?.unitLabel
            ?.takeIf { label -> label.isNotBlank() }
            ?: DEFAULT_WIND_SPEED_UNIT_LABEL,
        windTapLabelSize = windTapUiState.windTapLabelSize,
        onWindTapLabelSizeChanged = windTapUiState.onWindTapLabelSizeChanged,
        overlayViewportSize = windTapUiState.overlayViewportSize,
        forecastPointCallout = forecastWeatherState.forecastPointCallout,
        forecastSelectedRegionCode = forecastWeatherState.forecastOverlayState.selectedRegionCode,
        onDismissForecastPointCallout = forecastWeatherState.forecastViewModel::clearPointCallout,
        forecastQueryStatus = forecastWeatherState.forecastQueryStatus,
        onDismissForecastQueryStatus = forecastWeatherState.forecastViewModel::clearQueryStatus,
        qnhDialog = qnhUiState.dialogInputs,
        weGlidePrompt = MapWeGlidePromptInputs(
            prompt = weGlideUploadPrompt,
            onConfirm = onConfirmWeGlideUploadPrompt,
            onDismiss = onDismissWeGlideUploadPrompt
        )
    )
    ForecastOverlayRuntimeEffects(
        mapLibreMap = mapState.mapLibreMap,
        forecastOverlayState = forecastWeatherState.forecastOverlayState,
        rainViewerEnabled = forecastWeatherState.weatherOverlayState.enabled,
        overlayManager = overlayManager
    )

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                windTapUiState.onOverlayViewportSizeChanged(size)
            }
    ) {
        Scaffold(modifier = Modifier) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val maxWidthPx = with(density) { maxWidth.toPx() }
                val maxHeightPx = with(density) { maxHeight.toPx() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .size(maxWidth, maxHeight)
                ) {
                    MapOverlayStack(
                        mapState = mapState,
                        mapInitializer = mapInitializer,
                        onMapReady = onMapReady,
                        onMapViewBound = onMapViewBound,
                        locationManager = locationManager,
                        locationRenderFrameBinder = locationRenderFrameBinder,
                        flightDataManager = flightDataManager,
                        flightViewModel = flightViewModel,
                        taskType = taskType,
                        createTaskGestureHandler = createTaskGestureHandler,
                        windArrowState = windArrowState,
                        showWindSpeedOnVario = showWindSpeedOnVario,
                        cameraManager = cameraManager,
                        currentMode = currentMode,
                        currentZoom = currentZoom,
                        unitsPreferences = unitsPreferences,
                        onModeChange = onModeChange,
                        currentLocation = visibleCurrentLocation,
                        showReturnButton = localOwnshipRenderState.showReturnButton,
                        showDistanceCircles = showDistanceCircles,
                        ognOverlayEnabled = ognOverlayEnabled,
                        showOgnThermalsEnabled = showOgnThermalsEnabled,
                        overlayManager = overlayManager,
                        onOgnTargetSelected = onOgnTargetSelected,
                        onOgnThermalSelected = onOgnThermalSelected,
                        onAdsbTargetSelected = onAdsbTargetSelected,
                        onForecastWindArrowSpeedTap = windTapUiState.onForecastWindArrowSpeedTap,
                        onMapLongPress = { point ->
                            if (!isAATEditMode && forecastWeatherState.forecastOverlayState.enabled) {
                                forecastWeatherState.forecastViewModel.queryPointValue(
                                    latitude = point.latitude,
                                    longitude = point.longitude
                                )
                            }
                        },
                        isAATEditMode = isAATEditMode,
                        isUiEditMode = isUiEditMode,
                        onEditModeChange = onEditModeChange,
                        onEnterAATEditMode = onEnterAATEditMode,
                        onUpdateAATTargetPoint = onUpdateAATTargetPoint,
                        onExitAATEditMode = onExitAATEditMode,
                        safeContainerSize = safeContainerSize,
                        variometerUiState = variometerUiState,
                        minVariometerSizePx = minVariometerSizePx,
                        maxVariometerSizePx = maxVariometerSizePx,
                        onVariometerOffsetChange = onVariometerOffsetChange,
                        onVariometerSizeChange = onVariometerSizeChange,
                        onVariometerLongPress = onVariometerLongPress,
                        onVariometerEditFinished = onVariometerEditFinished,
                        hamburgerOffset = hamburgerOffset,
                        flightModeOffset = flightModeOffset,
                        settingsOffset = settingsOffset,
                        ballastOffset = ballastOffset,
                        hamburgerSizePx = hamburgerSizePx,
                        settingsSizePx = settingsSizePx,
                        onHamburgerOffsetChange = onHamburgerOffsetChange,
                        onFlightModeOffsetChange = onFlightModeOffsetChange,
                        onSettingsOffsetChange = onSettingsOffsetChange,
                        onBallastOffsetChange = onBallastOffsetChange,
                        onHamburgerSizeChange = onHamburgerSizeChange,
                        onSettingsSizeChange = onSettingsSizeChange,
                        widgetManager = widgetManager,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        modalManager = modalManager,
                        ballastUiState = ballastUiState,
                        hideBallastPill = isBallastPillHidden,
                        onBallastCommand = onBallastCommand,
                        onHamburgerTap = onHamburgerTap,
                        onHamburgerLongPress = onHamburgerLongPress,
                        onSettingsTap = onSettingsTap,
                        cardStyle = cardStyle,
                        hiddenCardIds = hiddenCardIds,
                        replayState = replayState
                    )
        }
            }
        }
        MapTaskManagerLayer(
            taskScreenManager = taskScreenManager,
            waypointData = waypointData,
            unitsPreferences = unitsPreferences,
            currentLocation = visibleCurrentLocation,
            currentQnh = qnhUiState.currentQnhLabel,
            taskFlightSurfaceUiState = taskFlightSurfaceUiState
        )

        MapActionButtonsLayer(
            currentLocation = visibleCurrentLocation,
            showRecenterButton = localOwnshipRenderState.showRecenterButton,
            showReturnButton = localOwnshipRenderState.showReturnButton,
            showVarioDemoFab = showVarioDemoFab && !hideReplayDebugFabs,
            showAatEditFab = isAATEditMode && taskType == TaskType.AAT,
            showRacingReplayFab = showRacingReplayFab && !hideReplayDebugFabs,
            onRecenter = locationManager::recenterOnCurrentLocation,
            onReturn = { locationManager.returnToSavedLocation() },
            onVarioDemoReferenceClick = onVarioDemoReferenceClick,
            onVarioDemoSimClick = onVarioDemoSimClick,
            onVarioDemoSim2Click = onVarioDemoSim2Click,
            onVarioDemoSim3Click = onVarioDemoSim3Click,
            onRacingReplayClick = onRacingReplayClick
        )

        MapBottomTabsSection(
            showMapBottomNavigation = showMapBottomNavigation,
            selectedBottomTab = bottomTabsUiState.selectedBottomTab,
            isBottomTabsSheetVisible = bottomTabsUiState.isBottomTabsSheetVisible,
            isTaskPanelVisible = bottomTabsUiState.isTaskPanelVisible,
            hasTrafficDetailsOpen = trafficContentUiState.hasTrafficDetailsOpen,
            setSelectedBottomTabName = bottomTabsUiState.setSelectedBottomTabName,
            setBottomTabsSheetVisible = bottomTabsUiState.setBottomTabsSheetVisible,
            onDismissOgnTargetDetails = onDismissOgnTargetDetails,
            onDismissOgnThermalDetails = onDismissOgnThermalDetails,
            onDismissAdsbTargetDetails = onDismissAdsbTargetDetails,
            weatherEnabled = forecastWeatherState.weatherOverlayState.enabled,
            ognOverlayEnabled = ognOverlayEnabled,
            showOgnSciaEnabled = showOgnSciaEnabled,
            onToggleOgnScia = onToggleOgnScia,
            adsbOverlayEnabled = adsbOverlayEnabled,
            showOgnThermalsEnabled = showOgnThermalsEnabled,
            showDistanceCircles = showDistanceCircles,
            currentQnhLabel = qnhUiState.currentQnhLabel,
            onToggleAdsbTraffic = onToggleAdsbTraffic,
            onToggleOgnThermals = onToggleOgnThermals,
            overlayManager = overlayManager,
            openQnhDialog = qnhUiState.openDialog,
            ognTrailAircraftRows = trafficContentUiState.ognTrailAircraftRows,
            onOgnTrailAircraftToggled = trafficRuntimeState.onTrailAircraftSelectionChanged,
            forecastOverlayState = forecastWeatherState.forecastOverlayState,
            forecastViewModel = forecastWeatherState.forecastViewModel,
            skySightWarningMessage = forecastWeatherState.skySightWarningMessage,
            skySightErrorMessage = forecastWeatherState.skySightErrorMessage,
            skySightSatViewEnabled = bottomTabsUiState.skySightSatViewEnabled,
            currentMapStyleName = currentMapStyleName,
            lastNonSatelliteMapStyleName = bottomTabsUiState.lastNonSatelliteMapStyleName,
            setLastNonSatelliteMapStyleName = bottomTabsUiState.setLastNonSatelliteMapStyleName,
            onTransientMapStyleSelected = onTransientMapStyleSelected
        )

        MapAuxiliaryPanelsAndSheetsSection(inputs = auxiliaryPanelsInputs)
        MapTrafficRuntimeLayer(
            traffic = trafficBinding,
            runtimeState = trafficRuntimeState,
            reserveTopEndPrimarySlot = false,
            ownshipCoordinate = visibleCurrentLocation?.let { location ->
                TrafficMapCoordinate(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            },
            unitsPreferences = unitsPreferences,
            trafficActions = trafficActions
        )
        MapLiveFollowRuntimeLayer(
            showPilotStatusIndicator = showPilotStatusIndicator,
            topEndAdditionalOffset = topEndPilotStatusOffset,
            currentZoom = currentZoom,
            taskRenderSnapshotProvider = taskRenderSnapshotProvider,
            watchedPilotFocusEpoch = watchedPilotFocusEpoch,
            mapLibreMapProvider = mapLibreMapProvider,
            mapViewProvider = { mapState.mapView },
            onFocusWatchedPilot = onFocusWatchedPilot
        )
    }
    ReplayDiagnosticsLogger(
        replayState = replayState,
        flightDataManager = flightDataManager,
        unitsPreferences = unitsPreferences
    )
}
