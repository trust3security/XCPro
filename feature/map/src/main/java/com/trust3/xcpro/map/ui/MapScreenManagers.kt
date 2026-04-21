package com.trust3.xcpro.map.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.trust3.xcpro.MapOrientationPreferences
import com.trust3.xcpro.map.LocationManager
import com.trust3.xcpro.map.MapDiagnosticsStatusSink
import com.trust3.xcpro.map.MapCameraRuntimePort
import com.trust3.xcpro.map.MapCameraManager
import com.trust3.xcpro.map.MapCameraUpdateGateAdapter
import com.trust3.xcpro.map.MapCameraPreferenceReaderAdapter
import com.trust3.xcpro.map.MapCameraSurfaceAdapter
import com.trust3.xcpro.map.MapLibreCameraControllerProvider
import com.trust3.xcpro.map.MapDisplayPoseSurfaceAdapter
import com.trust3.xcpro.map.MapInitializer
import com.trust3.xcpro.map.MapLifecycleRuntimePort
import com.trust3.xcpro.map.MapLifecycleManager
import com.trust3.xcpro.map.MapLifecycleSurfaceAdapter
import com.trust3.xcpro.map.MapLocationPreferencesAdapter
import com.trust3.xcpro.map.MapLocationRenderFrameBinderAdapter
import com.trust3.xcpro.map.MapLocationRenderFrameBinder
import com.trust3.xcpro.map.MapLocationFilter
import com.trust3.xcpro.map.MapLocationOverlayAdapter
import com.trust3.xcpro.map.MapLocationRuntimePort
import com.trust3.xcpro.map.MapModalManager
import com.trust3.xcpro.map.MapOverlayManager
import com.trust3.xcpro.map.MapRenderSurfaceDiagnostics
import com.trust3.xcpro.map.MapScreenState
import com.trust3.xcpro.map.MapScreenSizeProvider
import com.trust3.xcpro.map.MapStateActions
import com.trust3.xcpro.map.MapStateReader
import com.trust3.xcpro.map.MapOrientationRuntimePort
import com.trust3.xcpro.map.MapPhoneHealthUseCase
import com.trust3.xcpro.map.MapSensorsUseCase
import com.trust3.xcpro.map.MapTaskScreenManager
import com.trust3.xcpro.map.TaskRenderSyncCoordinator
import com.trust3.xcpro.map.LocationSensorsController
import com.trust3.xcpro.map.TaskRenderSnapshot
import com.trust3.xcpro.map.helpers.GliderPaddingHelper
import com.trust3.xcpro.map.trail.SnailTrailManager
import com.trust3.xcpro.map.ui.widgets.MapUIWidgetManager
import com.trust3.xcpro.replay.ReplayDisplayPose
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.airspace.AirspaceUseCase
import com.trust3.xcpro.flightdata.WaypointFilesUseCase
import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.map.diagnostics.DebugDiagnosticsFileExporter
import com.trust3.xcpro.tasks.TaskMapRenderRouter
import com.trust3.xcpro.tasks.core.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal data class MapScreenManagersTaskInputs(
    val taskRenderSnapshotProvider: () -> TaskRenderSnapshot,
    val taskWaypointCountProvider: () -> Int,
    val currentTaskProvider: () -> Task,
    val clearTask: () -> Unit,
    val saveTask: suspend (String) -> Boolean
)

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
    orientationRuntimePort: MapOrientationRuntimePort,
    onOrientationUserInteraction: () -> Unit,
    sensorsUseCase: MapSensorsUseCase,
    phoneHealthUseCase: MapPhoneHealthUseCase,
    replaySessionState: StateFlow<SessionState>,
    replayHeadingProvider: (Long) -> Double?,
    replayFixProvider: (Long) -> ReplayDisplayPose?,
    featureFlags: MapFeatureFlags,
    useRenderFrameSyncProvider: () -> Boolean,
    coroutineScope: CoroutineScope,
    taskInputs: MapScreenManagersTaskInputs,
    airspaceUseCase: AirspaceUseCase,
    waypointFilesUseCase: WaypointFilesUseCase,
    localOwnshipRenderEnabled: () -> Boolean
): MapScreenManagers {
    val snailTrailManager = remember(mapState, context, featureFlags) {
        SnailTrailManager(context, mapState, featureFlags)
    }

    val taskRenderSyncCoordinator = remember(taskInputs, mapState) {
        TaskRenderSyncCoordinator(
            snapshotProvider = taskInputs.taskRenderSnapshotProvider,
            mapProvider = { mapState.mapLibreMap },
            renderSync = TaskMapRenderRouter::syncTaskVisuals,
            renderClear = TaskMapRenderRouter::clearAllTaskVisuals,
            renderAatPreview = TaskMapRenderRouter::previewAatTargetPoint
        )
    }

    val renderSurfaceDiagnostics = remember { MapRenderSurfaceDiagnostics() }
    val diagnosticsFileExporter = remember(context) {
        DebugDiagnosticsFileExporter(context)
    }
    val diagnosticsStatusSink = remember(diagnosticsFileExporter) {
        MapDiagnosticsStatusSink { status ->
            diagnosticsFileExporter.appendLine(status)
        }
    }

    val overlayManager = remember(
        mapState,
        taskInputs,
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
            taskInputs.taskWaypointCountProvider,
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

    val taskScreenManager = remember(mapState, taskInputs, coroutineScope) {
        MapTaskScreenManager(
            mapState = mapState,
            currentTaskProvider = taskInputs.currentTaskProvider,
            clearTaskAction = taskInputs.clearTask,
            saveTaskAction = taskInputs.saveTask,
            coroutineScope = coroutineScope
        )
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
    val sensorsPort = remember(context, coroutineScope, sensorsUseCase, phoneHealthUseCase) {
        LocationSensorsController(
            context = context,
            scope = coroutineScope,
            sensorsUseCase = sensorsUseCase,
            phoneHealthUseCase = phoneHealthUseCase
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

    val locationRenderFrameBinder = remember(useRenderFrameSyncProvider, locationManager, renderSurfaceDiagnostics) {
        MapLocationRenderFrameBinderAdapter(
            useRenderFrameSync = useRenderFrameSyncProvider,
            onRenderFrame = { locationManager.onRenderFrame() },
            renderSurfaceDiagnostics = renderSurfaceDiagnostics
        )
    }

    val lifecycleManager = remember(
        mapState,
        mapStateActions,
        orientationRuntimePort,
        locationManager,
        locationRenderFrameBinder,
        diagnosticsStatusSink,
        replaySessionState
    ) {
        MapLifecycleManager(
            lifecycleSurface = MapLifecycleSurfaceAdapter(
                mapState = mapState,
                stateActions = mapStateActions
            ),
            orientationManager = orientationRuntimePort,
            locationManager = locationManager,
            locationRenderFrameCleanup = locationRenderFrameBinder,
            renderSurfaceDiagnostics = renderSurfaceDiagnostics,
            diagnosticsStatusSink = diagnosticsStatusSink,
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
        onOrientationUserInteraction,
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
            onOrientationUserInteraction = onOrientationUserInteraction,
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
