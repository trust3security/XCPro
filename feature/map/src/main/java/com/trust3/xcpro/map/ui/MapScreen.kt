package com.trust3.xcpro.map.ui

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.navigation.NavHostController
import com.trust3.xcpro.map.MapScreenViewModel

@Composable
fun MapScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    profileExpanded: MutableState<Boolean>,
    mapStyleExpanded: MutableState<Boolean>,
    settingsExpanded: MutableState<Boolean>,
    allowFlightSensorStart: Boolean,
    isGeneralSettingsVisible: Boolean,
    onMapStyleSelected: (String) -> Unit = {},
    onOpenGeneralSettings: () -> Unit = {},
    mapViewModel: MapScreenViewModel
) {
    MapScreenRoot(
        navController = navController,
        drawerState = drawerState,
        profileExpanded = profileExpanded,
        mapStyleExpanded = mapStyleExpanded,
        settingsExpanded = settingsExpanded,
        allowFlightSensorStart = allowFlightSensorStart,
        isGeneralSettingsVisible = isGeneralSettingsVisible,
        onMapStyleSelected = onMapStyleSelected,
        onOpenGeneralSettings = onOpenGeneralSettings,
        mapViewModel = mapViewModel
    )
}

