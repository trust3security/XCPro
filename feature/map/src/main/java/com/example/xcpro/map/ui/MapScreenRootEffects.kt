package com.example.xcpro.map.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.example.xcpro.airspace.AirspaceUiState
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.ogn.OgnTrafficTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun MapScreenBackHandler(
    drawerState: androidx.compose.material3.DrawerState,
    modalManager: MapModalManager,
    taskScreenManager: MapTaskScreenManager,
    isTaskPanelVisible: Boolean,
    navController: NavHostController,
    coroutineScope: CoroutineScope
) {
    BackHandler(enabled = drawerState.isOpen || modalManager.isAnyModalOpen() || isTaskPanelVisible) {
        when {
            drawerState.isOpen -> {
                coroutineScope.launch { drawerState.close() }
            }

            modalManager.handleBackGesture() -> Unit
            taskScreenManager.handleBackGesture() -> Unit
            else -> navController.popBackStack()
        }
    }
}

@Composable
internal fun MapScreenOverlayEffects(
    mapState: MapScreenState,
    airspaceState: AirspaceUiState,
    overlayManager: MapOverlayManager,
    ognTargets: List<OgnTrafficTarget>,
    ognOverlayEnabled: Boolean,
    ognIconSizePx: Int,
    adsbTargets: List<AdsbTrafficUiModel>,
    adsbOverlayEnabled: Boolean,
    adsbIconSizePx: Int
) {
    LaunchedEffect(mapState.mapLibreMap, airspaceState.enabledFiles, airspaceState.classStates) {
        overlayManager.refreshAirspace(mapState.mapLibreMap)
    }
    LaunchedEffect(ognTargets, ognOverlayEnabled) {
        overlayManager.updateOgnTrafficTargets(
            if (ognOverlayEnabled) ognTargets else emptyList()
        )
    }
    LaunchedEffect(ognIconSizePx) {
        overlayManager.setOgnIconSizePx(ognIconSizePx)
    }
    LaunchedEffect(adsbTargets, adsbOverlayEnabled) {
        overlayManager.updateAdsbTrafficTargets(
            if (adsbOverlayEnabled) adsbTargets else emptyList()
        )
    }
    LaunchedEffect(adsbIconSizePx) {
        overlayManager.setAdsbIconSizePx(adsbIconSizePx)
    }
}

@Composable
internal fun MapVisibilityLifecycleEffect(mapViewModel: MapScreenViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapViewModel) {
        mapViewModel.setMapVisible(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> mapViewModel.setMapVisible(true)

                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> mapViewModel.setMapVisible(false)

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewModel.setMapVisible(false)
        }
    }
}
