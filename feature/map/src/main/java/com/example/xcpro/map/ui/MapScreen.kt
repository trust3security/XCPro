package com.example.xcpro.map.ui

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.navigation.NavHostController
import com.example.xcpro.map.MapScreenViewModel

@Composable
fun MapScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    profileExpanded: MutableState<Boolean>,
    mapStyleExpanded: MutableState<Boolean>,
    settingsExpanded: MutableState<Boolean>,
    initialMapStyle: String,
    onMapStyleSelected: (String) -> Unit = {},
    mapViewModel: MapScreenViewModel
) {
    MapScreenRoot(
        navController = navController,
        drawerState = drawerState,
        profileExpanded = profileExpanded,
        mapStyleExpanded = mapStyleExpanded,
        settingsExpanded = settingsExpanded,
        initialMapStyle = initialMapStyle,
        onMapStyleSelected = onMapStyleSelected,
        mapViewModel = mapViewModel
    )
}

