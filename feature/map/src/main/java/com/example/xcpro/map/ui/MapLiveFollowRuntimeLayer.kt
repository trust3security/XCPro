package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.livefollow.pilot.LiveFollowPilotMapStatusHost
import com.example.xcpro.livefollow.pilot.LiveFollowPilotViewModel
import com.example.xcpro.livefollow.watch.LiveFollowMapRenderState
import com.example.xcpro.livefollow.watch.LiveFollowTaskRenderPolicy
import com.example.xcpro.livefollow.watch.LiveFollowWatchMapHost
import com.example.xcpro.livefollow.watch.LiveFollowWatchUiState
import com.example.xcpro.livefollow.watch.LiveFollowWatchViewModel
import com.example.xcpro.map.LiveFollowWatchAircraftOverlay
import com.example.xcpro.map.LiveFollowWatchAircraftOverlayState
import com.example.xcpro.map.LiveFollowWatchTaskOverlay
import com.example.xcpro.map.LiveFollowWatchTaskOverlayState
import com.example.xcpro.map.TaskRenderSnapshot
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.maps.MapView

internal data class MapLiveFollowTaskAttachmentState(
    val attachTask: Boolean,
    val overlayState: LiveFollowWatchTaskOverlayState? = null
)

internal data class MapLiveFollowFocusTarget(
    val shareCode: String,
    val latitudeDeg: Double,
    val longitudeDeg: Double
)

internal fun resolveMapLiveFollowTaskAttachmentState(
    uiState: LiveFollowWatchUiState
): MapLiveFollowTaskAttachmentState {
    val taskOverlayState = resolveMapLiveFollowWatchTaskOverlayState(uiState)
    val taskRenderPolicy = uiState.mapRenderState.taskRenderPolicy
    return when (taskRenderPolicy) {
        LiveFollowTaskRenderPolicy.AVAILABLE -> MapLiveFollowTaskAttachmentState(
            attachTask = taskOverlayState != null,
            overlayState = taskOverlayState
        )

        LiveFollowTaskRenderPolicy.BLOCKED_AMBIGUOUS,
        LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE ->
            MapLiveFollowTaskAttachmentState(
                attachTask = false,
                overlayState = null
            )

        LiveFollowTaskRenderPolicy.HIDDEN -> MapLiveFollowTaskAttachmentState(
            attachTask = false,
            overlayState = null
        )
    }
}

internal fun resolveMapLiveFollowWatchOverlayState(
    uiState: LiveFollowWatchUiState
): LiveFollowWatchAircraftOverlayState? {
    val mapRenderState = uiState.mapRenderState
    if (!mapRenderState.isVisible) return null
    val latitudeDeg = mapRenderState.latitudeDeg ?: return null
    val longitudeDeg = mapRenderState.longitudeDeg ?: return null
    val mapShareCode = normalizeWatchShareCode(mapRenderState.shareCode)
    val selectedShareCode = normalizedWatchSelectionShareCode(uiState)
    val resolvedShareCode = mapShareCode ?: selectedShareCode ?: return null
    if (
        selectedShareCode != null &&
        mapShareCode != null &&
        selectedShareCode != mapShareCode
    ) {
        return null
    }
    return LiveFollowWatchAircraftOverlayState(
        shareCode = resolvedShareCode,
        latitudeDeg = latitudeDeg,
        longitudeDeg = longitudeDeg,
        trackDeg = mapRenderState.trackDeg
    )
}

internal fun resolveMapLiveFollowFocusTarget(
    uiState: LiveFollowWatchUiState,
    lastFocusedShareCode: String?,
    watchedPilotFocusEpoch: Int
): MapLiveFollowFocusTarget? {
    if (watchedPilotFocusEpoch <= 0) return null
    val overlayState = resolveMapLiveFollowWatchOverlayState(uiState) ?: return null
    if (normalizeWatchShareCode(overlayState.shareCode) == normalizeWatchShareCode(lastFocusedShareCode)) {
        return null
    }
    return MapLiveFollowFocusTarget(
        shareCode = overlayState.shareCode,
        latitudeDeg = overlayState.latitudeDeg,
        longitudeDeg = overlayState.longitudeDeg
    )
}

internal fun resolveMapLiveFollowWatchTaskOverlayState(
    uiState: LiveFollowWatchUiState
): LiveFollowWatchTaskOverlayState? {
    val mapRenderState = uiState.mapRenderState
    if (!mapRenderState.isVisible) return null
    if (mapRenderState.taskRenderPolicy != LiveFollowTaskRenderPolicy.AVAILABLE) return null
    val taskSnapshot = mapRenderState.taskSnapshot?.takeIf { it.isRenderable() } ?: return null
    val mapShareCode = normalizeWatchShareCode(mapRenderState.shareCode)
    val selectedShareCode = normalizedWatchSelectionShareCode(uiState)
    val resolvedShareCode = mapShareCode ?: selectedShareCode ?: return null
    if (
        selectedShareCode != null &&
        mapShareCode != null &&
        selectedShareCode != mapShareCode
    ) {
        return null
    }
    return LiveFollowWatchTaskOverlayState(
        shareCode = resolvedShareCode,
        points = taskSnapshot.points
    )
}

@Composable
internal fun BoxScope.MapLiveFollowRuntimeLayer(
    showPilotStatusIndicator: Boolean,
    topEndAdditionalOffset: Dp,
    currentZoomFlow: StateFlow<Float>,
    taskRenderSnapshotProvider: () -> TaskRenderSnapshot,
    watchedPilotFocusEpoch: Int,
    mapLibreMapProvider: () -> org.maplibre.android.maps.MapLibreMap?,
    mapViewProvider: () -> MapView?,
    onFocusWatchedPilot: (Double, Double) -> Boolean
) {
    val watchViewModel: LiveFollowWatchViewModel = hiltViewModel()
    val uiState by watchViewModel.uiState.collectAsStateWithLifecycle()
    val currentZoom by currentZoomFlow.collectAsStateWithLifecycle()
    val taskAttachmentState = remember(
        uiState
    ) {
        resolveMapLiveFollowTaskAttachmentState(
            uiState = uiState
        )
    }
    val watchedPilotOverlayState = remember(uiState) {
        resolveMapLiveFollowWatchOverlayState(uiState)
    }
    val mapLibreMap = mapLibreMapProvider()
    val mapView = mapViewProvider()
    val appContext = LocalContext.current.applicationContext
    val watchAircraftOverlay = remember(mapLibreMap, appContext) {
        mapLibreMap?.let { LiveFollowWatchAircraftOverlay(map = it, context = appContext) }
    }
    val watchTaskOverlay = remember(mapLibreMap) {
        mapLibreMap?.let { LiveFollowWatchTaskOverlay(map = it) }
    }
    var lastFocusedShareCode by remember { mutableStateOf<String?>(null) }

    DisposableEffect(watchAircraftOverlay) {
        onDispose {
            watchAircraftOverlay?.cleanup()
        }
    }
    DisposableEffect(watchTaskOverlay) {
        onDispose {
            watchTaskOverlay?.cleanup()
        }
    }
    LaunchedEffect(watchAircraftOverlay, currentZoom) {
        watchAircraftOverlay?.setViewportZoom(currentZoom)
    }
    LaunchedEffect(watchAircraftOverlay, watchedPilotOverlayState) {
        watchAircraftOverlay?.render(watchedPilotOverlayState)
    }
    DisposableEffect(mapView, watchAircraftOverlay, watchedPilotOverlayState, currentZoom) {
        val currentMapView = mapView
        val currentOverlay = watchAircraftOverlay
        if (currentMapView == null || currentOverlay == null) {
            onDispose {}
        } else {
            val listener = MapView.OnDidFinishLoadingStyleListener {
                currentOverlay.setViewportZoom(currentZoom)
                currentOverlay.reapplyCurrentStyle()
            }
            currentMapView.addOnDidFinishLoadingStyleListener(listener)
            onDispose {
                currentMapView.removeOnDidFinishLoadingStyleListener(listener)
            }
        }
    }
    LaunchedEffect(watchTaskOverlay, taskAttachmentState.overlayState) {
        watchTaskOverlay?.render(taskAttachmentState.overlayState)
    }
    LaunchedEffect(uiState.selectedShareCode, uiState.shareCode) {
        val selectedShareCode = normalizedWatchSelectionShareCode(uiState)
        if (
            selectedShareCode == null ||
            selectedShareCode != normalizeWatchShareCode(lastFocusedShareCode)
        ) {
            lastFocusedShareCode = null
        }
    }
    LaunchedEffect(watchedPilotFocusEpoch) {
        if (watchedPilotFocusEpoch > 0) {
            lastFocusedShareCode = null
        }
    }
    LaunchedEffect(watchedPilotOverlayState, lastFocusedShareCode, watchedPilotFocusEpoch) {
        val focusTarget = resolveMapLiveFollowFocusTarget(
            uiState = uiState,
            lastFocusedShareCode = lastFocusedShareCode,
            watchedPilotFocusEpoch = watchedPilotFocusEpoch
        ) ?: return@LaunchedEffect
        val focusApplied = onFocusWatchedPilot(
            focusTarget.latitudeDeg,
            focusTarget.longitudeDeg
        )
        if (focusApplied) {
            lastFocusedShareCode = focusTarget.shareCode
        }
    }
    if (showPilotStatusIndicator) {
        val pilotViewModel: LiveFollowPilotViewModel = hiltViewModel()
        val pilotUiState by pilotViewModel.uiState.collectAsStateWithLifecycle()
        LiveFollowPilotMapStatusHost(
            visible = true,
            topEndAdditionalOffset = topEndAdditionalOffset,
            uiState = pilotUiState,
            onStartSharing = pilotViewModel::startSharing,
            onStopSharing = pilotViewModel::stopSharing
        )
    }

    LiveFollowWatchMapHost(
        uiState = uiState
    )
}

private fun normalizeWatchShareCode(rawShareCode: String?): String? {
    val trimmed = rawShareCode?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return trimmed.uppercase(Locale.US)
}

private fun normalizedWatchSelectionShareCode(
    uiState: LiveFollowWatchUiState
): String? {
    return normalizeWatchShareCode(uiState.selectedShareCode ?: uiState.shareCode)
}
