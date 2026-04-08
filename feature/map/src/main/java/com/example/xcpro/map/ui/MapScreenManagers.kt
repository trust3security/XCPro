package com.example.xcpro.map.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.xcpro.MapOrientationPreferences
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.MapCameraRuntimePort
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapCameraUpdateGateAdapter
import com.example.xcpro.map.MapCameraPreferenceReaderAdapter
import com.example.xcpro.map.MapCameraSurfaceAdapter
import com.example.xcpro.map.MapLibreCameraControllerProvider
import com.example.xcpro.map.MapDisplayPoseSurfaceAdapter
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapLifecycleRuntimePort
import com.example.xcpro.map.MapLifecycleManager
import com.example.xcpro.map.MapLifecycleSurfaceAdapter
import com.example.xcpro.map.MapLocationPreferencesAdapter
import com.example.xcpro.map.MapLocationRenderFrameBinderAdapter
import com.example.xcpro.map.MapLocationRenderFrameBinder
import com.example.xcpro.map.MapLocationFilter
import com.example.xcpro.map.MapLocationOverlayAdapter
import com.example.xcpro.map.MapLocationRuntimePort
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapRenderSurfaceDiagnostics
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenSizeProvider
import com.example.xcpro.map.MapStateActions
import com.example.xcpro.map.MapStateReader
import com.example.xcpro.map.MapSensorsUseCase
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.MapTasksUseCase
import com.example.xcpro.map.TaskRenderSyncCoordinator
import com.example.xcpro.map.LocationSensorsController
import com.example.xcpro.map.helpers.GliderPaddingHelper
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.replay.ReplayDisplayPose
import com.example.xcpro.replay.SessionState
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.tasks.TaskMapRenderRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal data class MapScreenManagers(
    val snailTrailManager: SnailTrailManager,
    val overlayManager: MapOverlayManager,
    val widgetManager: MapUIWidgetManager,
    val taskScreenManager: MapTaskScreenManager,
    val cameraManager: MapCameraRuntimePort,
    val locationManager: MapLocationRuntimePort,
    val locationRenderFrameBinder: MapLocationRenderFrameBinder,
    val renderSurfaceDiagnostics: MapRenderSurfaceDiagnostics,
    val lifecycleManager: MapLifecycleRuntimePort,
    val modalManager: MapModalManager,
    val mapInitializer: MapInitializer
)

@Composable
internal fun rememberMapScreenManagers(
    context: Context,
    mapState: MapScreenState,
    mapStateReader: MapStateReader,
    mapStateActions: MapStateActions,
    orientationManager: MapOrientationManager,
    sensorsUseCase: MapSensorsUseCase,
    replaySessionState: StateFlow<SessionState>,
    replayHeadingProvider: (Long) -> Double?,
    replayFixProvider: (Long) -> ReplayDisplayPose?,
    featureFlags: MapFeatureFlags,
    coroutineScope: CoroutineScope,
    tasksUseCase: MapTasksUseCase,
    airspaceUseCase: AirspaceUseCase,
    waypointFilesUseCase: WaypointFilesUseCase,
    localOwnshipRenderEnabled: () -> Boolean
): MapScreenManagers {
    val snailTrailManager = remember(mapState, context, featureFlags) {
        SnailTrailManager(context, mapState, featureFlags)
    }

    val taskRenderSyncCoordinator = remember(tasksUseCase, mapState) {
        TaskRenderSyncCoordinator(
            snapshotProvider = tasksUseCase::taskRenderSnapshot,
            mapProvider = { mapState.mapLibreMap },
            renderSync = TaskMapRenderRouter::syncTaskVisuals,
            renderClear = TaskMapRenderRouter::clearAllTaskVisuals,
            renderAatPreview = TaskMapRenderRouter::previewAatTargetPoint
        )
    }

    val renderSurfaceDiagnostics = remember { MapRenderSurfaceDiagnostics() }

    val overlayManager = remember(
        mapState,
        tasksUseCase,
        context,
        mapStateReader,
        mapStateActions,
        taskRenderSyncCoordinator,
        snailTrailManager,
        coroutineScope,
        airspaceUseCase,
        waypointFilesUseCase,
        renderSurfaceDiagnostics
    ) {
        MapOverlayManager(
            context,
            mapState,
            mapStateReader,
            taskRenderSyncCoordinator,
            { tasksUseCase.currentRuntimeSnapshot().task.waypoints.size },
            mapStateActions,
            snailTrailManager,
            coroutineScope,
            airspaceUseCase,
            waypointFilesUseCase,
            renderSurfaceDiagnostics = renderSurfaceDiagnostics
        )
    }

    val widgetManager = remember(mapState) {
        MapUIWidgetManager(mapState)
    }

    val taskScreenManager = remember(mapState, tasksUseCase, coroutineScope) {
        MapTaskScreenManager(mapState, tasksUseCase, coroutineScope)
    }

    val cameraSurface = remember(mapState) {
        MapCameraSurfaceAdapter(mapState)
    }

    val cameraManager = remember(cameraSurface, mapStateReader, mapStateActions) {
        MapCameraManager(cameraSurface, mapStateReader, mapStateActions)
    }

    val orientationPreferences = remember(context) {
        MapOrientationPreferences(context)
    }
    val gliderPaddingHelper = remember(context, orientationPreferences) {
        GliderPaddingHelper(context.resources, orientationPreferences)
    }
    val cameraPreferenceReader = remember(orientationPreferences) {
        MapCameraPreferenceReaderAdapter(orientationPreferences)
    }
    val locationPreferences = remember(orientationPreferences) {
        MapLocationPreferencesAdapter(orientationPreferences)
    }
    val sensorsPort = remember(context, coroutineScope, sensorsUseCase) {
        LocationSensorsController(
            context = context,
            scope = coroutineScope,
            sensorsUseCase = sensorsUseCase
        )
    }
    val cameraControllerProvider = remember(mapState) {
        MapLibreCameraControllerProvider(mapState)
    }
    val mapViewSizeProvider = remember(mapState) {
        MapScreenSizeProvider(mapState)
    }
    val cameraUpdateGate = remember(mapState, featureFlags) {
        MapCameraUpdateGateAdapter(
            gate = MapLocationFilter(
                config = MapLocationFilter.Config(
                    thresholdPx = featureFlags.locationJitterThresholdPx,
                    historySize = featureFlags.locationOffsetHistorySize
                )
            ),
            mapProvider = { mapState.mapLibreMap }
        )
    }
    val locationOverlayPort = remember(mapState) {
        MapLocationOverlayAdapter(mapState)
    }
    val displayPoseSurface = remember(mapState) {
        MapDisplayPoseSurfaceAdapter(mapState)
    }

    val locationManager = remember(
        mapStateReader,
        featureFlags,
        cameraPreferenceReader,
        locationPreferences,
        sensorsPort,
        cameraControllerProvider,
        mapViewSizeProvider,
        cameraUpdateGate,
        locationOverlayPort,
        displayPoseSurface
    ) {
        LocationManager(
            mapStateReader = mapStateReader,
            stateActions = mapStateActions,
            featureFlags = featureFlags,
            cameraPreferenceReader = cameraPreferenceReader,
            locationPreferences = locationPreferences,
            paddingProvider = gliderPaddingHelper::paddingArray,
            sensorsPort = sensorsPort,
            cameraControllerProvider = cameraControllerProvider,
            mapViewSizeProvider = mapViewSizeProvider,
            cameraUpdateGate = cameraUpdateGate,
            locationOverlayPort = locationOverlayPort,
            displayPoseSurfacePort = displayPoseSurface,
            renderSurfaceDiagnostics = renderSurfaceDiagnostics,
            localOwnshipRenderEnabledProvider = localOwnshipRenderEnabled,
            replayHeadingProvider = replayHeadingProvider,
            replayFixProvider = replayFixProvider
        )
    }

    val locationRenderFrameBinder = remember(featureFlags, locationManager, renderSurfaceDiagnostics) {
        MapLocationRenderFrameBinderAdapter(
            useRenderFrameSync = { featureFlags.useRenderFrameSync },
            onRenderFrame = { locationManager.onRenderFrame() },
            renderSurfaceDiagnostics = renderSurfaceDiagnostics
        )
    }

    val lifecycleManager = remember(
        mapState,
        mapStateActions,
        orientationManager,
        locationManager,
        locationRenderFrameBinder,
        replaySessionState
    ) {
        MapLifecycleManager(
            lifecycleSurface = MapLifecycleSurfaceAdapter(
                mapState = mapState,
                stateActions = mapStateActions
            ),
            orientationManager = orientationManager,
            locationManager = locationManager,
            locationRenderFrameCleanup = locationRenderFrameBinder,
            renderSurfaceDiagnostics = renderSurfaceDiagnostics,
            replaySessionState = replaySessionState
        )
    }

    val modalManager = remember(mapState) {
        MapModalManager(mapState)
    }

    val mapInitializer = remember(
        mapState,
        mapStateReader,
        mapStateActions,
        overlayManager,
        orientationManager,
        taskRenderSyncCoordinator,
        context,
        coroutineScope,
        snailTrailManager,
        airspaceUseCase,
        waypointFilesUseCase
    ) {
        MapInitializer(
            context = context,
            mapState = mapState,
            mapStateReader = mapStateReader,
            stateActions = mapStateActions,
            overlayManager = overlayManager,
            orientationManager = orientationManager,
            taskRenderSyncCoordinator = taskRenderSyncCoordinator,
            snailTrailManager = snailTrailManager,
            coroutineScope = coroutineScope,
            airspaceUseCase = airspaceUseCase,
            waypointFilesUseCase = waypointFilesUseCase,
            localOwnshipRenderEnabledProvider = localOwnshipRenderEnabled
        )
    }

    return MapScreenManagers(
        snailTrailManager = snailTrailManager,
        overlayManager = overlayManager,
        widgetManager = widgetManager,
        taskScreenManager = taskScreenManager,
        cameraManager = cameraManager,
        locationManager = locationManager,
        locationRenderFrameBinder = locationRenderFrameBinder,
        renderSurfaceDiagnostics = renderSurfaceDiagnostics,
        lifecycleManager = lifecycleManager,
        modalManager = modalManager,
        mapInitializer = mapInitializer
    )
}
