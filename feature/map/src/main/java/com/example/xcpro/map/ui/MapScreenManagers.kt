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
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.MapOrientationManager
import kotlinx.coroutines.CoroutineScope

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
    taskManager: TaskManagerCoordinator,
    mapStateActions: MapStateActions,
    orientationManager: MapOrientationManager,
    varioServiceManager: VarioServiceManager,
    igcReplayController: IgcReplayController,
    coroutineScope: CoroutineScope
): MapScreenManagers {
    val snailTrailManager = remember(mapState, context) {
        SnailTrailManager(context, mapState)
    }

    val overlayManager = remember(
        mapState,
        taskManager,
        context,
        mapStateReader,
        mapStateActions,
        snailTrailManager,
        coroutineScope
    ) {
        MapOverlayManager(
            context,
            mapState,
            mapStateReader,
            taskManager,
            mapStateActions,
            snailTrailManager,
            coroutineScope
        )
    }

    val widgetPrefs = remember(context) {
        context.getSharedPreferences(MAP_PREFS_NAME, Context.MODE_PRIVATE)
    }
    val widgetManager = remember(mapState, widgetPrefs) {
        MapUIWidgetManager(mapState, widgetPrefs)
    }

    val taskScreenManager = remember(mapState, taskManager) {
        MapTaskScreenManager(mapState, taskManager)
    }

    val cameraManager = remember(mapState, mapStateReader, mapStateActions) {
        MapCameraManager(mapState, mapStateReader, mapStateActions)
    }

    val locationManager = remember(
        mapState,
        mapStateReader,
        varioServiceManager,
        context
    ) {
        LocationManager(
            context = context,
            mapState = mapState,
            mapStateReader = mapStateReader,
            stateActions = mapStateActions,
            coroutineScope = coroutineScope,
            varioServiceManager = varioServiceManager
        )
    }

    val lifecycleManager = remember(
        mapState,
        orientationManager,
        locationManager,
        igcReplayController
    ) {
        MapLifecycleManager(mapState, orientationManager, locationManager, igcReplayController)
    }

    val modalManager = remember(mapState) {
        MapModalManager(mapState)
    }

    val mapInitializer = remember(
        mapState,
        mapStateReader,
        mapStateActions,
        orientationManager,
        taskManager,
        context,
        coroutineScope,
        snailTrailManager
    ) {
        MapInitializer(
            context = context,
            mapState = mapState,
            mapStateReader = mapStateReader,
            stateActions = mapStateActions,
            orientationManager = orientationManager,
            taskManager = taskManager,
            snailTrailManager = snailTrailManager,
            coroutineScope = coroutineScope
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

private const val MAP_PREFS_NAME = "MapPrefs"
