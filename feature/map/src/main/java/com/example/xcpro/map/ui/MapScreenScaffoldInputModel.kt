package com.example.xcpro.map.ui

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.MutableState
import androidx.navigation.NavHostController
import com.example.xcpro.map.model.GpsStatusUiModel

internal data class MapScreenScaffoldChromeBindings(
    val scaffold: MapScreenScaffoldChromeInputs,
    val shared: MapScreenScaffoldSharedInputs
)

internal data class MapScreenScaffoldSharedInputs(
    val showMapBottomNavigation: Boolean,
    val shouldBlockDrawerOpen: Boolean,
    val onOpenGeneralSettingsFromMap: () -> Unit
)

internal data class MapScreenScaffoldChromeInputs(
    val drawerState: DrawerState,
    val navController: NavHostController,
    val profileExpanded: MutableState<Boolean>,
    val mapStyleExpanded: MutableState<Boolean>,
    val settingsExpanded: MutableState<Boolean>,
    val selectedMapStyle: String,
    val onDrawerItemSelected: (String) -> Unit,
    val onMapStyleSelected: (String) -> Unit,
    val gpsStatus: GpsStatusUiModel,
    val isLoadingWaypoints: Boolean,
    val onOpenGeneralSettingsFromDrawer: () -> Unit
)
