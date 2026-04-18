package com.trust3.xcpro.map.ui

import android.util.Log
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.trust3.xcpro.livefollow.LiveFollowRoutes
import com.trust3.xcpro.map.MapScreenViewModel
import com.trust3.xcpro.map.MapTaskIntegration
import com.trust3.xcpro.map.MapTaskScreenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val MapScreenScaffoldInputsTag = "MapScreen"

@Composable
internal fun rememberMapScreenScaffoldChromeBindings(
    coroutineScope: CoroutineScope,
    navController: NavHostController,
    drawerState: DrawerState,
    profileExpanded: MutableState<Boolean>,
    mapStyleExpanded: MutableState<Boolean>,
    settingsExpanded: MutableState<Boolean>,
    onMapStyleSelected: (String) -> Unit,
    onOpenGeneralSettings: () -> Unit,
    mapViewModel: MapScreenViewModel,
    rootUiBinding: MapScreenRootUiBinding,
    bindings: MapScreenBindings,
    taskScreenManager: MapTaskScreenManager
): MapScreenScaffoldChromeBindings {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val showMapBottomNavigation = remember(navBackStackEntry?.destination?.route) {
        isMapBottomNavigationRoute(navBackStackEntry?.destination?.route)
    }
    val mapBindings = bindings.map
    val taskBindings = bindings.task
    val mapUiState = rootUiBinding.mapUiState
    val onDrawerItemSelected: (String) -> Unit = { item ->
        Log.d(MapScreenScaffoldInputsTag, "Navigation drawer item selected: $item")
        coroutineScope.launch {
            drawerState.close()
            taskScreenManager.handleNavigationTaskSelection(item)
        }
    }
    val onResolvedMapStyleSelected: (String) -> Unit = { style ->
        mapViewModel.setMapStyle(style)
        coroutineScope.launch { mapViewModel.persistMapStyle(style) }
        Log.d(MapScreenScaffoldInputsTag, "Map style selected: $style")
        onMapStyleSelected(style)
    }
    val shouldBlockDrawerOpen = MapTaskIntegration.shouldBlockDrawerGestures(
        taskType = taskBindings.taskType,
        isAATEditMode = taskBindings.isAATEditMode
    )
    val openGeneralSettingsFromMap: () -> Unit = {
        if (!shouldBlockDrawerOpen) {
            settingsExpanded.value = true
            coroutineScope.launch {
                if (drawerState.isOpen) {
                    drawerState.close()
                }
                onOpenGeneralSettings()
            }
        }
    }

    return MapScreenScaffoldChromeBindings(
        scaffold = MapScreenScaffoldChromeInputs(
            drawerState = drawerState,
            navController = navController,
            profileExpanded = profileExpanded,
            mapStyleExpanded = mapStyleExpanded,
            settingsExpanded = settingsExpanded,
            selectedMapStyle = mapBindings.baseMapStyleName,
            onDrawerItemSelected = onDrawerItemSelected,
            onMapStyleSelected = onResolvedMapStyleSelected,
            gpsStatus = mapBindings.gpsStatus,
            isLoadingWaypoints = mapUiState.isLoadingWaypoints,
            onOpenGeneralSettingsFromDrawer = openGeneralSettingsFromMap
        ),
        shared = MapScreenScaffoldSharedInputs(
            showMapBottomNavigation = showMapBottomNavigation,
            shouldBlockDrawerOpen = shouldBlockDrawerOpen,
            onOpenGeneralSettingsFromMap = openGeneralSettingsFromMap
        )
    )
}

internal fun isMapBottomNavigationRoute(route: String?): Boolean {
    val normalizedRoute = route?.substringBefore("?")
    return normalizedRoute in MAP_BOTTOM_NAVIGATION_ROUTES
}

private val MAP_BOTTOM_NAVIGATION_ROUTES = setOf(
    LiveFollowRoutes.MAP_ROUTE,
    LiveFollowRoutes.FRIENDS_FLYING
)
