package com.example.xcpro.map.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapLifecycleManager
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapStateActions
import com.example.xcpro.map.MapStateReader
import com.example.xcpro.map.MapSensorsUseCase
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.MapTasksUseCase
import com.example.xcpro.map.TaskRenderSyncCoordinator
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
    val cameraManager: MapCameraManager,
    val locationManager: LocationManager,
    val lifecycleManager: MapLifecycleManager,
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
    waypointFilesUseCase: WaypointFilesUseCase
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
        waypointFilesUseCase
    ) {
        MapOverlayManager(
            context,
            mapState,
            mapStateReader,
            taskRenderSyncCoordinator,
            tasksUseCase::currentWaypointCount,
            mapStateActions,
            snailTrailManager,
            coroutineScope,
            airspaceUseCase,
            waypointFilesUseCase
        )
    }

    val widgetManager = remember(mapState) {
        MapUIWidgetManager(mapState)
    }

    val taskScreenManager = remember(mapState, tasksUseCase, coroutineScope) {
        MapTaskScreenManager(mapState, tasksUseCase, coroutineScope)
    }

    val cameraManager = remember(mapState, mapStateReader, mapStateActions) {
        MapCameraManager(mapState, mapStateReader, mapStateActions)
    }

    val locationManager = remember(
        mapState,
        mapStateReader,
        sensorsUseCase,
        context,
        featureFlags
    ) {
        LocationManager(
            context = context,
            mapState = mapState,
            mapStateReader = mapStateReader,
            stateActions = mapStateActions,
            coroutineScope = coroutineScope,
            sensorsUseCase = sensorsUseCase,
            featureFlags = featureFlags,
            replayHeadingProvider = replayHeadingProvider,
            replayFixProvider = replayFixProvider
        )
    }

    val lifecycleManager = remember(
        mapState,
        orientationManager,
        locationManager,
        replaySessionState,
        mapStateActions
    ) {
        MapLifecycleManager(
            mapState = mapState,
            orientationManager = orientationManager,
            locationManager = locationManager,
            replaySessionState = replaySessionState,
            stateActions = mapStateActions
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
            waypointFilesUseCase = waypointFilesUseCase
        )
    }

    return MapScreenManagers(
        snailTrailManager = snailTrailManager,
        overlayManager = overlayManager,
        widgetManager = widgetManager,
        taskScreenManager = taskScreenManager,
        cameraManager = cameraManager,
        locationManager = locationManager,
        lifecycleManager = lifecycleManager,
        modalManager = modalManager,
        mapInitializer = mapInitializer
    )
}
