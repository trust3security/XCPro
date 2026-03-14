package com.example.xcpro.map.ui

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.MutableState
import androidx.navigation.NavHostController
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.model.GpsStatusUiModel

internal data class MapScreenScaffoldInputs(
    val scaffold: MapScreenScaffoldChromeInputs,
    val content: MapScreenContentInputs
)

internal data class MapScreenScaffoldChromeInputs(
    val drawerState: DrawerState,
    val navController: NavHostController,
    val profileExpanded: MutableState<Boolean>,
    val mapStyleExpanded: MutableState<Boolean>,
    val settingsExpanded: MutableState<Boolean>,
    val initialMapStyle: String,
    val onDrawerItemSelected: (String) -> Unit,
    val onMapStyleSelected: (String) -> Unit,
    val gpsStatus: GpsStatusUiModel,
    val isLoadingWaypoints: Boolean,
    val modalManager: MapModalManager,
    val onOpenGeneralSettingsFromDrawer: () -> Unit
)
