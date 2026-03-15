package com.example.xcpro.appshell.settings

import androidx.compose.material3.DrawerState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.example.xcpro.navigation.MapNavigationSignals

private const val MAP_ROUTE = "map"

internal fun requestOpenGeneralSettingsOnMap(navController: NavHostController) {
    runCatching { navController.getBackStackEntry(MAP_ROUTE) }
        .getOrNull()
        ?.savedStateHandle
        ?.set(MapNavigationSignals.OPEN_GENERAL_SETTINGS_ON_MAP, true)
}

internal fun consumeOpenGeneralSettingsOnMap(entry: NavBackStackEntry): Boolean {
    val shouldOpen = entry.savedStateHandle
        .get<Boolean>(MapNavigationSignals.OPEN_GENERAL_SETTINGS_ON_MAP) == true
    if (shouldOpen) {
        entry.savedStateHandle[MapNavigationSignals.OPEN_GENERAL_SETTINGS_ON_MAP] = false
    }
    return shouldOpen
}

internal suspend fun closeGeneralToMap(
    navController: NavHostController,
    drawerState: DrawerState
) {
    drawerState.close()
    val poppedToMap = navController.popBackStack(MAP_ROUTE, inclusive = false)
    if (!poppedToMap) {
        navController.navigateUp()
    }
}

internal suspend fun closeGeneralToDrawer(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val poppedToMap = navController.popBackStack(MAP_ROUTE, inclusive = false)
    if (!poppedToMap) {
        navController.navigateUp()
    }
    drawerState.open()
}
